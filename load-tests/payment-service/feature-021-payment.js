import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = (__ENV.PAYMENT_K6_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const paymentId = __ENV.PAYMENT_K6_PAYMENT_ID || '';
const ownerToken = __ENV.PAYMENT_K6_OWNER_TOKEN || '';
const foreignToken = __ENV.PAYMENT_K6_FOREIGN_TOKEN || '';
const scenario = (__ENV.PAYMENT_K6_SCENARIO || 'owner-query').toLowerCase();
const targetVus = Number(__ENV.PAYMENT_K6_TARGET_VUS || 20);
const stageDuration = __ENV.PAYMENT_K6_STAGE_DURATION || '30s';
const warmupDuration = __ENV.PAYMENT_K6_WARMUP || '15s';
const ownerP95 = Number(__ENV.PAYMENT_K6_OWNER_P95_MS || 300);
const checkoutP95 = Number(__ENV.PAYMENT_K6_CHECKOUT_P95_MS || 1500);
const checkoutIdempotencyKey = __ENV.PAYMENT_K6_CHECKOUT_KEY || 'feature021-k6-checkout-replay';
const expectedPaymentStatuses = http.expectedStatuses(200, 404);

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: Math.max(1, Math.min(targetVus, 5)),
      duration: warmupDuration,
      tags: { phase: 'warmup' },
      exec: 'ownerQuery',
    },
    staged: {
      executor: 'ramping-vus',
      startTime: warmupDuration,
      startVUs: 0,
      stages: [
        { duration: stageDuration, target: Math.max(1, Math.floor(targetVus / 2)) },
        { duration: stageDuration, target: targetVus },
        { duration: stageDuration, target: 0 },
      ],
      gracefulRampDown: '10s',
      tags: { phase: 'staged' },
      exec: scenario === 'checkout' ? 'checkout' : 'ownerQuery',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    payment_owner_query: [`p(95)<${ownerP95}`],
    payment_checkout_local: [`p(95)<${checkoutP95}`],
    payment_non_enumeration: ['rate==1'],
    payment_unexpected_errors: ['count==0'],
  },
};

export const ownerQueryDuration = new Trend('payment_owner_query', true);
export const checkoutLocalDuration = new Trend('payment_checkout_local', true);
export const nonEnumeration = new Rate('payment_non_enumeration');
export const unexpectedErrors = new Counter('payment_unexpected_errors');

export function setup() {
  if (!paymentId || !ownerToken) {
    throw new Error('PAYMENT_K6_PAYMENT_ID and PAYMENT_K6_OWNER_TOKEN are required.');
  }
  if (__ENV.PAYMENT_K6_TOKEN_FILE) {
    throw new Error('Token files are not accepted; pass short-lived tokens through environment variables.');
  }
  for (const token of [ownerToken, foreignToken]) {
    if (token && /sk_|whsec_|checkout\.stripe|\s/.test(token)) {
      throw new Error('Refusing a secret, provider URL, or multiline token in k6 input.');
    }
  }
  return { paymentId };
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'X-Trace-Id': `feature021-k6-${__VU}-${__ITER}`,
  };
}

export function ownerQuery(data) {
  const started = Date.now();
  const response = http.get(`${baseUrl}/api/v1/payments/${data.paymentId}`, {
    headers: authHeaders(ownerToken),
    tags: { operation: 'owner-query' },
    responseCallback: expectedPaymentStatuses,
  });
  ownerQueryDuration.add(Date.now() - started);
  const safe = !/(checkoutUrl|providerSessionId|secret|signature|rawBody|authorization)/i.test(response.body);
  const ownerOk = check(response, {
    'owner query is 200': (value) => value.status === 200,
    'owner response is safe': () => safe,
  });
  if (!ownerOk) unexpectedErrors.add(1);

  if (foreignToken && __ITER % 10 === 0) {
    const foreign = http.get(`${baseUrl}/api/v1/payments/${data.paymentId}`, {
      headers: authHeaders(foreignToken),
      tags: { operation: 'foreign-query' },
      responseCallback: expectedPaymentStatuses,
    });
    const unknown = http.get(`${baseUrl}/api/v1/payments/00000000-0000-0000-0000-000000000000`, {
      headers: authHeaders(foreignToken),
      tags: { operation: 'unknown-query' },
      responseCallback: expectedPaymentStatuses,
    });
    const sameError = foreign.status === 404 && unknown.status === 404
      && String(foreign.body).match(/PAYMENT_NOT_FOUND/)
      && String(unknown.body).match(/PAYMENT_NOT_FOUND/);
    nonEnumeration.add(Boolean(sameError));
    if (!sameError) unexpectedErrors.add(1);
  }
  sleep(0.05);
}

export function checkout(data) {
  const started = Date.now();
  const response = http.post(`${baseUrl}/api/v1/payments/${data.paymentId}/checkout-sessions`, null, {
    headers: {
      ...authHeaders(ownerToken),
      // This profile measures the service-local replay path. The separate 100-concurrent
      // integration test exercises distinct keys and the one-active-attempt invariant.
      'Idempotency-Key': checkoutIdempotencyKey,
    },
    tags: { operation: 'checkout-create' },
  });
  checkoutLocalDuration.add(Date.now() - started);
  const safe = !/(secret|signature|rawBody|authorization)/i.test(response.body);
  const accepted = check(response, {
    'checkout is accepted': (value) => [200, 201, 202].includes(value.status),
    'checkout response is safe': () => safe,
  });
  if (!accepted) unexpectedErrors.add(1);
  sleep(0.05);
}
