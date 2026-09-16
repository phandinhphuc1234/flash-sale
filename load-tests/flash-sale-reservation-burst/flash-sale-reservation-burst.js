import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import encoding from 'k6/encoding';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// This profile tests only the Flash Sale reservation boundary. It deliberately does not
// query Order, call Payment, or read a database. Use the PowerShell runner with -Run.
if (__ENV.FSB_RUN !== '1') throw new Error('Use run-flash-sale-reservation-burst.ps1 -Run.');

const cfg = JSON.parse(__ENV.FSB_SETTINGS);
const tokens = new SharedArray('flash-sale burst shoppers', () => JSON.parse(open(__ENV.FSB_TOKEN_FILE)));
const baseUrl = cfg.baseUrl.replace(/\/$/, '');

const accepted = new Counter('fsb_accepted_reservations');
const soldOut = new Counter('fsb_sold_out');
const pending = new Counter('fsb_acceptance_pending');
const unexpected = new Counter('fsb_unexpected_responses');
const transportErrors = new Counter('fsb_transport_errors');
const expectedOutcomes = new Rate('fsb_expected_outcome_rate');
const reservationLatency = new Trend('fsb_reservation_latency_ms', true);

export const options = {
  scenarios: {
    hot_sku_burst: {
      executor: 'constant-arrival-rate',
      rate: cfg.rate,
      timeUnit: '1s',
      duration: `${cfg.duration}s`,
      preAllocatedVUs: cfg.vus,
      maxVUs: cfg.maxVUs,
      gracefulStop: `${cfg.gracefulStop}s`,
    },
  },
  summaryTrendStats: ['min', 'max', 'avg', 'p(95)', 'p(99)'],
  systemTags: ['name', 'method', 'status', 'scenario'],
  maxRedirects: 0,
  thresholds: {
    fsb_unexpected_responses: ['count==0'],
    fsb_transport_errors: ['count==0'],
    fsb_expected_outcome_rate: ['rate>=0.99'],
    dropped_iterations: ['count==0'],
  },
};

function tokenClaims(token) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('invalid_token_shape');
  const payload = encoding.b64decode(parts[1], 'rawurl', 's');
  return JSON.parse(payload);
}

function responseBody(response) {
  try { return response.json(); } catch (_) { return null; }
}

export default function () {
  const index = exec.scenario.iterationInTest;
  const token = tokens[index];
  if (!token) {
    unexpected.add(1);
    expectedOutcomes.add(false);
    return;
  }

  try {
    const claims = tokenClaims(token);
    if (!claims.sub || !claims.exp || claims.exp * 1000 < Date.now() + cfg.duration * 1000 + 30_000) {
      throw new Error('shopper_token_expiring');
    }
    const response = http.post(
      `${baseUrl}/api/v1/flash-sales/${cfg.campaignId}/reservations`,
      JSON.stringify({ variantId: cfg.variantId, quantity: 1 }),
      {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          'Idempotency-Key': `fsb-${cfg.runId}-${index}`,
          'X-Trace-Id': `fsb-${cfg.runId}-${index}`,
        },
        timeout: `${cfg.requestSeconds}s`,
        redirects: 0,
        tags: { operation: 'flash_sale_reservation_burst' },
      },
    );
    reservationLatency.add(response.timings.duration);

    if (response.status === 0) {
      transportErrors.add(1);
      expectedOutcomes.add(false);
      return;
    }

    const body = responseBody(response);
    if (response.status === 202 && body?.success === true && body?.data?.reservationId && body?.data?.purchaseRequestId) {
      accepted.add(1);
      expectedOutcomes.add(true);
      check(response, { 'reservation winner is a durable 202': () => true });
      return;
    }

    if (response.status === 409 && body?.errorCode === 'FLASH_SALE_SOLD_OUT') {
      soldOut.add(1);
      expectedOutcomes.add(true);
      check(response, { 'sold-out response is expected': () => true });
      return;
    }

    if (response.status === 503 && body?.errorCode === 'FLASH_SALE_ACCEPTANCE_PENDING') {
      pending.add(1);
      expectedOutcomes.add(true);
      check(response, { 'acceptance-pending response is observable': () => true });
      return;
    }

    unexpected.add(1);
    expectedOutcomes.add(false);
    check(response, { 'response is an accepted Flash Sale outcome': () => false });
  } catch (_) {
    transportErrors.add(1);
    expectedOutcomes.add(false);
  }
}

function metricValues(data, name) {
  return data.metrics[name]?.values || {};
}

export function handleSummary(data) {
  const report = {
    schemaVersion: 1,
    test: 'flash-sale-reservation-burst',
    generatedAt: new Date().toISOString(),
    runId: cfg.runId,
    baseUrl: cfg.baseUrl,
    campaignId: cfg.campaignId,
    variantId: cfg.variantId,
    rate: cfg.rate,
    durationSeconds: cfg.duration,
    plannedArrivals: cfg.rate * cfg.duration,
    expectedAllocation: cfg.expectedAllocation,
    counters: {
      acceptedReservations: metricValues(data, 'fsb_accepted_reservations').count || 0,
      soldOut: metricValues(data, 'fsb_sold_out').count || 0,
      acceptancePending: metricValues(data, 'fsb_acceptance_pending').count || 0,
      unexpectedResponses: metricValues(data, 'fsb_unexpected_responses').count || 0,
      transportErrors: metricValues(data, 'fsb_transport_errors').count || 0,
      droppedIterations: metricValues(data, 'dropped_iterations').count || 0,
    },
    expectedOutcomeRate: metricValues(data, 'fsb_expected_outcome_rate').rate ?? null,
    latencyMs: {
      p95: metricValues(data, 'fsb_reservation_latency_ms')['p(95)'] || null,
      p99: metricValues(data, 'fsb_reservation_latency_ms')['p(99)'] || null,
    },
    notes: [
      'Reservation-only profile; Order and Payment are intentionally excluded.',
      'A 202 is a winner, 409 FLASH_SALE_SOLD_OUT is an expected loser, and 503 FLASH_SALE_ACCEPTANCE_PENDING is reported separately.',
      'Accepted reservations must be compared with the fixture allocation before claiming no oversell.',
    ],
  };
  return {
    stdout: `FLASH_SALE_RESERVATION_BURST=${JSON.stringify(report)}\n`,
    [__ENV.FSB_SUMMARY_FILE]: JSON.stringify(report, null, 2),
  };
}
