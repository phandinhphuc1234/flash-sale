import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = (__ENV.ORDER_BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const orderId = required('ORDER_ID');
const ownerTokens = readTokens(required('ORDER_OWNER_TOKEN_FILE'), 'owner');
const foreignTokens = readTokens(required('ORDER_FOREIGN_TOKEN_FILE'), 'foreign');
const p95Threshold = numberEnv('ORDER_P95_THRESHOLD_MS', 200);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    owner_detail: {
      executor: 'per-vu-iterations',
      vus: integerEnv('ORDER_VUS', Math.min(ownerTokens.length, 50)),
      iterations: integerEnv('ORDER_ITERATIONS', 20),
      maxDuration: __ENV.ORDER_MAX_DURATION || '2m',
    },
  },
  thresholds: {
    order_owner_detail_duration: [`p(95)<${p95Threshold}`],
    order_unexpected_errors: ['count==0'],
    order_checks: ['rate==1'],
  },
};

const ownerDetailDuration = new Trend('order_owner_detail_duration', true);
const unexpectedErrors = new Counter('order_unexpected_errors');
const ownerChecks = new Rate('order_checks');
const foreignChecks = new Counter('order_foreign_non_enumerating');

function required(name) {
  const value = __ENV[name];
  if (!value) fail(`${name} is required.`);
  return value;
}

function integerEnv(name, fallback) {
  const raw = __ENV[name];
  const value = raw ? Number.parseInt(raw, 10) : fallback;
  if (!Number.isInteger(value) || value <= 0) fail(`${name} must be a positive integer.`);
  return value;
}

function numberEnv(name, fallback) {
  const raw = __ENV[name];
  const value = raw ? Number.parseFloat(raw) : fallback;
  if (!Number.isFinite(value) || value <= 0) fail(`${name} must be positive.`);
  return value;
}

function readTokens(path, label) {
  const values = new SharedArray(`${label} order tokens`, () => {
    const parsed = JSON.parse(open(path));
    if (Array.isArray(parsed)) return parsed;
    if (typeof parsed === 'string') return [parsed];
    if (Array.isArray(parsed.tokens)) return parsed.tokens;
    if (parsed.token) return [parsed.token];
    fail(`${label} token file must contain a token, array, or {tokens:[...]}.`);
  });
  if (!Array.isArray(values) || values.length === 0 || values.some((token) => !token)) {
    fail(`${label} token file must contain non-empty access tokens.`);
  }
  return values;
}

function request(token, path, tag) {
  return http.get(`${baseUrl}${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Trace-Id': traceId(),
    },
    tags: { operation: 'order_query', path: tag },
  });
}

function traceId() {
  // __VU and __ITER are only available in the VU execution context, not setup().
  const vu = typeof __VU === 'number' ? __VU : 0;
  const iteration = typeof __ITER === 'number' ? __ITER : 0;
  const phase = vu === 0 ? 'setup' : 'vu';
  return `k6-order-${phase}-${vu}-${iteration}`;
}

export function setup() {
  const foreignToken = foreignTokens[0];
  const foreign = request(foreignToken, `/api/v1/orders/${orderId}`, 'foreign-detail');
  const unknown = request(foreignToken, `/api/v1/orders/${__ENV.ORDER_UNKNOWN_ID || '00000000-0000-0000-0000-000000000000'}`, 'unknown-detail');
  const foreignBody = safeJson(foreign);
  const unknownBody = safeJson(unknown);
  const valid = check(null, {
    'foreign order is non-enumerating 404': () => foreign.status === 404 && foreignBody?.errorCode === 'ORDER_NOT_FOUND',
    'unknown order is non-enumerating 404': () => unknown.status === 404 && unknownBody?.errorCode === 'ORDER_NOT_FOUND',
  });
  if (!valid) fail(`Owner non-enumeration setup failed: foreign=${foreign.status}, unknown=${unknown.status}.`);
  foreignChecks.add(1);
  return { baseUrl, orderId };
}

export default function () {
  const token = ownerTokens[(__VU - 1) % ownerTokens.length];
  const response = request(token, `/api/v1/orders/${orderId}`, 'owner-detail');
  ownerDetailDuration.add(response.timings.duration);
  const body = safeJson(response);
  const valid = check(response, {
    'owner order detail returns 200': (res) => res.status === 200,
    'owner response contains requested order': () => body?.success === true && body?.data?.id === orderId,
    'owner response is no-store': (res) => res.headers['Cache-Control'] === 'no-store',
    'owner response has trace id': (res) => Boolean(res.headers['X-Trace-Id']),
  });
  ownerChecks.add(valid ? 1 : 0);
  if (!valid) unexpectedErrors.add(1);
}

function safeJson(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify({
      generatedAt: new Date().toISOString(),
      baseUrl,
      orderId,
      p95ThresholdMs: p95Threshold,
      metrics: data.metrics,
      notes: [
        'Tokens are loaded from local files and are never emitted in the summary.',
        'Run owner and foreign tokens from the same approved fixture; do not commit token files.',
        'The threshold is an evidence gate for this local topology, not a universal production SLA.',
      ],
    }, null, 2),
    [__ENV.ORDER_K6_SUMMARY_FILE || 'load-tests/order-service/order-query-summary.json']:
      JSON.stringify(data, null, 2),
  };
}
