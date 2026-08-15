import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = __ENV.FLASHSALE_BASE_URL || 'http://127.0.0.1:18080';
const campaignId = required('FLASHSALE_CAMPAIGN_ID');
const variantId = required('FLASHSALE_VARIANT_ID');
const expectedAllocation = integerEnv('FLASHSALE_EXPECTED_ALLOCATION', 100);
const tokenFile = __ENV.FLASHSALE_SHOPPER_TOKENS_FILE || 'shopper-tokens.json';
const route = __ENV.FLASHSALE_ROUTE || (baseUrl.includes(':18080') ? 'gateway' : 'direct-service');
const runLabel = __ENV.FLASHSALE_RUN_LABEL || 'measurement';
const tokens = new SharedArray('flashsale shoppers', () => JSON.parse(open(tokenFile)));

if (!Array.isArray(tokens) || tokens.length === 0) {
  fail('FLASHSALE_SHOPPER_TOKENS_FILE must contain a non-empty JSON array of access tokens.');
}

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    reservations: {
      executor: 'per-vu-iterations',
      vus: integerEnv('FLASHSALE_VUS', Math.min(tokens.length, expectedAllocation * 10)),
      iterations: integerEnv('FLASHSALE_ITERATIONS_PER_VU', 1),
      maxDuration: __ENV.FLASHSALE_MAX_DURATION || '2m',
    },
  },
  thresholds: {
    // These guard protocol correctness only. They intentionally make no universal RPS/latency claim.
    'reservation_unexpected_errors': ['count==0'],
    'reservation_successful_winners': [`count<=${expectedAllocation}`],
  },
};

const successfulWinners = new Counter('reservation_successful_winners');
const replays = new Counter('reservation_replays');
const soldOut = new Counter('reservation_sold_out');
const pending = new Counter('reservation_acceptance_pending');
const unexpectedErrors = new Counter('reservation_unexpected_errors');
const responseLatency = new Trend('reservation_http_duration', true);
const winnerLatency = new Trend('reservation_winner_http_duration', true);
const replayLatency = new Trend('reservation_replay_http_duration', true);
const responseErrorRate = new Rate('reservation_error_rate');
const winnerErrorRate = new Rate('reservation_winner_error_rate');
const replayErrorRate = new Rate('reservation_replay_error_rate');

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

function idempotencyKey() {
  // The key is unique per logical attempt. A second request with the same key below proves replay.
  return `k6-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function submit(token, key, path, latencyMetric) {
  const response = http.post(
    `${baseUrl}/api/v1/flash-sales/${campaignId}/reservations`,
    JSON.stringify({ variantId, quantity: 1 }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
        'X-Trace-Id': `k6-${__VU}-${__ITER}`,
      },
      tags: { operation: 'reservation_submit', path },
    },
  );
  responseLatency.add(response.timings.duration);
  latencyMetric.add(response.timings.duration);
  return response;
}

export default function () {
  const token = tokens[(__VU - 1) % tokens.length];
  const key = idempotencyKey();
  const first = submit(token, key, 'winner', winnerLatency);

  if (first.status === 202) {
    const body = first.json();
    const valid = check(first, {
      '202 contains durable reservation identity': (response) =>
        body?.success === true && Boolean(body?.data?.reservationId) && Boolean(body?.data?.purchaseRequestId),
      '202 includes Location and trace identity': (response) =>
        Boolean(response.headers.Location) && Boolean(response.headers['X-Trace-Id']),
    });
    if (!valid) {
      unexpectedErrors.add(1);
      responseErrorRate.add(1);
      winnerErrorRate.add(1);
      return;
    }
    successfulWinners.add(1);
    winnerErrorRate.add(0);

    const replay = submit(token, key, 'replay', replayLatency);
    const replayBody = replay.json();
    const replayValid = check(replay, {
      'same key replays 202': (response) => response.status === 202,
      'same key preserves reservation ID': () => replayBody?.data?.reservationId === body.data.reservationId,
      'same key preserves purchase request ID': () => replayBody?.data?.purchaseRequestId === body.data.purchaseRequestId,
    });
    if (replayValid) replays.add(1);
    else unexpectedErrors.add(1);
    responseErrorRate.add(replayValid ? 0 : 1);
    replayErrorRate.add(replayValid ? 0 : 1);
    return;
  }

  if (first.status === 409 && first.json()?.errorCode === 'FLASH_SALE_SOLD_OUT') {
    soldOut.add(1);
    responseErrorRate.add(0);
    winnerErrorRate.add(0);
    return;
  }
  if (first.status === 503 && first.json()?.errorCode === 'FLASH_SALE_ACCEPTANCE_PENDING') {
    pending.add(1);
    responseErrorRate.add(0);
    winnerErrorRate.add(0);
    return;
  }

  unexpectedErrors.add(1);
  responseErrorRate.add(1);
  winnerErrorRate.add(1);
  check(first, { 'unexpected response is reported': () => false });
}

export function handleSummary(data) {
  const summary = {
    generatedAt: new Date().toISOString(),
    campaignId,
    variantId,
    baseUrl,
    route,
    runLabel,
    expectedAllocation,
    tokensProvided: tokens.length,
    metrics: data.metrics,
    notes: [
      'This result is hardware/topology-specific evidence, not a production SLA.',
      'Compare successful_winners with PostgreSQL reservations and Redis remaining quota after the run.',
      'Only 202 is a durable winner; sold-out and acceptance-pending are expected terminal/retryable outcomes.',
    ],
  };
  return {
    stdout: JSON.stringify(summary, null, 2),
    [__ENV.FLASHSALE_K6_SUMMARY_FILE || 'load-tests/flashsale-service/reservation-summary.json']:
      JSON.stringify(summary, null, 2),
  };
}
