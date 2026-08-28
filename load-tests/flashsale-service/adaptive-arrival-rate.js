import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = (__ENV.FLASHSALE_BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const campaignId = required('FLASHSALE_CAMPAIGN_ID');
const variantId = required('FLASHSALE_VARIANT_ID');
const expectedAllocation = integerEnv('FLASHSALE_EXPECTED_ALLOCATION', 100000);
const targetRate = integerEnv('FLASHSALE_RATE', 25);
const stageDurationSeconds = integerEnv('FLASHSALE_STAGE_DURATION_SECONDS', 30);
const preAllocatedVUs = integerEnv('FLASHSALE_PREALLOCATED_VUS', Math.max(25, targetRate));
const maxVUs = integerEnv('FLASHSALE_MAX_VUS', Math.max(preAllocatedVUs, targetRate * 4));
const expectedOutcomeRate = numberEnv('FLASHSALE_EXPECTED_OUTCOME_RATE', 0.99);
const tokenFile = required('FLASHSALE_SHOPPER_TOKENS_FILE');
const tokenOffset = nonNegativeIntegerEnv('FLASHSALE_TOKEN_OFFSET', 0);
const route = __ENV.FLASHSALE_ROUTE || 'gateway';
const runLabel = __ENV.FLASHSALE_RUN_LABEL || `rate-${targetRate}`;
const tokens = new SharedArray('adaptive flashsale shoppers', () => JSON.parse(open(tokenFile)));

if (!Array.isArray(tokens) || tokens.length === 0) {
  fail('FLASHSALE_SHOPPER_TOKENS_FILE must contain a non-empty JSON array of access tokens.');
}
if (maxVUs < preAllocatedVUs) {
  fail('FLASHSALE_MAX_VUS must be greater than or equal to FLASHSALE_PREALLOCATED_VUS.');
}

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    reservations: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration: `${stageDurationSeconds}s`,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '0s',
    },
  },
  thresholds: {
    adaptive_unexpected_errors: ['count==0'],
    dropped_iterations: ['count==0'],
    adaptive_expected_outcome_rate: [`rate>=${expectedOutcomeRate}`],
    adaptive_successful_winners: [`count<=${expectedAllocation}`],
  },
};

const successfulWinners = new Counter('adaptive_successful_winners');
const replays = new Counter('adaptive_replays');
const soldOut = new Counter('adaptive_sold_out');
const pending = new Counter('adaptive_acceptance_pending');
const unexpectedErrors = new Counter('adaptive_unexpected_errors');
const expectedOutcomes = new Rate('adaptive_expected_outcome_rate');
const responseLatency = new Trend('adaptive_http_duration', true);
const winnerLatency = new Trend('adaptive_winner_http_duration', true);
const replayLatency = new Trend('adaptive_replay_http_duration', true);

function required(name) {
  const value = __ENV[name];
  if (!value) fail(`${name} is required.`);
  return value;
}

function integerEnv(name, fallback) {
  const raw = __ENV[name];
  const parsed = raw ? Number.parseInt(raw, 10) : fallback;
  if (!Number.isInteger(parsed) || parsed <= 0) fail(`${name} must be a positive integer.`);
  return parsed;
}

function nonNegativeIntegerEnv(name, fallback) {
  const raw = __ENV[name];
  const parsed = raw ? Number.parseInt(raw, 10) : fallback;
  if (!Number.isInteger(parsed) || parsed < 0) fail(`${name} must be a non-negative integer.`);
  return parsed;
}

function numberEnv(name, fallback) {
  const raw = __ENV[name];
  const parsed = raw ? Number.parseFloat(raw) : fallback;
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 1) fail(`${name} must be between 0 and 1.`);
  return parsed;
}

function idempotencyKey() {
  return `adaptive-${runLabel}-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function submit(token, key, operation, latencyMetric) {
  const response = http.post(
    `${baseUrl}/api/v1/flash-sales/${campaignId}/reservations`,
    JSON.stringify({ variantId, quantity: 1 }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
        'X-Trace-Id': `adaptive-${runLabel}-${__VU}-${__ITER}`,
      },
      tags: { operation: 'reservation_submit', route, stage: runLabel, path: operation },
    },
  );
  responseLatency.add(response.timings.duration);
  latencyMetric.add(response.timings.duration);
  return response;
}

function bodyOf(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

export default function () {
  const tokenIndex = tokenOffset + exec.scenario.iterationInTest;
  if (tokenIndex >= tokens.length) {
    fail('FLASHSALE_SHOPPER_TOKENS_FILE does not contain enough unique tokens for this stage.');
  }
  const token = tokens[tokenIndex];
  const key = idempotencyKey();
  const first = submit(token, key, 'winner', winnerLatency);
  const firstBody = bodyOf(first);

  if (first.status === 202) {
    expectedOutcomes.add(1);
    const valid = check(first, {
      'winner is a durable 202': () => firstBody?.success === true && Boolean(firstBody?.data?.reservationId) && Boolean(firstBody?.data?.purchaseRequestId),
      'winner includes Location and trace identity': (response) => Boolean(response.headers.Location) && Boolean(response.headers['X-Trace-Id']),
    });
    if (!valid) {
      unexpectedErrors.add(1);
      return;
    }
    successfulWinners.add(1);

    const replay = submit(token, key, 'replay', replayLatency);
    const replayBody = bodyOf(replay);
    const replayValid = check(replay, {
      'replay returns 202': (response) => response.status === 202,
      'replay preserves reservation identity': () => replayBody?.data?.reservationId === firstBody.data.reservationId,
      'replay preserves purchase-request identity': () => replayBody?.data?.purchaseRequestId === firstBody.data.purchaseRequestId,
    });
    if (replayValid) replays.add(1);
    else unexpectedErrors.add(1);
    return;
  }

  if (first.status === 409 && firstBody?.errorCode === 'FLASH_SALE_SOLD_OUT') {
    expectedOutcomes.add(1);
    soldOut.add(1);
    return;
  }
  if (first.status === 503 && firstBody?.errorCode === 'FLASH_SALE_ACCEPTANCE_PENDING') {
    expectedOutcomes.add(1);
    pending.add(1);
    return;
  }

  expectedOutcomes.add(0);
  unexpectedErrors.add(1);
  check(first, { 'unexpected response is reported': () => false });
}

function metricValues(data, name) {
  return data.metrics[name]?.values || {};
}

export function handleSummary(data) {
  const metrics = data.metrics;
  return {
    stdout: JSON.stringify({
      schemaVersion: 1,
      generatedAt: new Date().toISOString(),
      route,
      runLabel,
      targetRate,
      stageDurationSeconds,
      expectedAllocation,
      tokensProvided: tokens.length,
      metrics,
      notes: [
        'Topology-specific capacity evidence; not a production SLA.',
        'Only 202 is a durable winner; 409 sold-out and 503 acceptance-pending are expected outcomes.',
      ],
    }, null, 2),
    [__ENV.FLASHSALE_K6_SUMMARY_FILE || 'adaptive-arrival-rate-summary.json']: JSON.stringify({
      schemaVersion: 1,
      generatedAt: new Date().toISOString(),
      route,
      runLabel,
      targetRate,
      stageDurationSeconds,
      expectedAllocation,
      tokensProvided: tokens.length,
      metrics,
      derived: {
        winnerP95Ms: metricValues(data, 'adaptive_winner_http_duration')['p(95)'] || null,
        winnerP99Ms: metricValues(data, 'adaptive_winner_http_duration')['p(99)'] || null,
        replayP95Ms: metricValues(data, 'adaptive_replay_http_duration')['p(95)'] || null,
        replayP99Ms: metricValues(data, 'adaptive_replay_http_duration')['p(99)'] || null,
        droppedIterations: metricValues(data, 'dropped_iterations').count || 0,
      },
    }, null, 2),
  };
}
