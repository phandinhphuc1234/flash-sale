import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const defaultTargets = [
  { name: 'api-gateway', url: 'http://127.0.0.1:18080' },
  { name: 'authentication-service', url: 'http://127.0.0.1:18081' },
  { name: 'product-service', url: 'http://127.0.0.1:18082' },
  { name: 'campaign-service', url: 'http://127.0.0.1:18083' },
  { name: 'flashsale-service', url: 'http://127.0.0.1:18084' },
  { name: 'order-service', url: 'http://127.0.0.1:18085' },
  { name: 'payment-service', url: 'http://127.0.0.1:18086' },
  { name: 'notification-service', url: 'http://127.0.0.1:18087' },
  { name: 'inventory-service', url: 'http://127.0.0.1:18088' },
  { name: 'cart-service', url: 'http://127.0.0.1:18089' },
];

const targets = parseTargets(__ENV.FULL_STACK_TARGETS);
const rate = positiveInt(__ENV.FULL_STACK_RATE, 1);
const durationSeconds = positiveInt(__ENV.FULL_STACK_DURATION_SECONDS, 20);
const requestTimeout = __ENV.FULL_STACK_REQUEST_TIMEOUT || '5s';

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    readiness: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: `${durationSeconds}s`,
      // One iteration fans out to ten services. Keep enough headroom for a single
      // cold JVM/readiness response without turning this smoke into an unbounded load test.
      preAllocatedVUs: Math.max(4, rate * 2),
      maxVUs: Math.max(8, rate * 4),
      gracefulStop: '5s',
    },
  },
  thresholds: {
    full_stack_readiness_failure: ['count==0'],
    http_req_failed: ['rate==0'],
    dropped_iterations: ['count==0'],
  },
};

const readinessFailures = new Counter('full_stack_readiness_failure');
const checkedServices = new Counter('full_stack_services_checked');
const healthyResponses = new Rate('full_stack_healthy_response');
const readinessLatency = new Trend('full_stack_readiness_duration', true);

export default function () {
  for (const target of targets) {
    const response = http.get(`${target.url}/actuator/health/readiness`, {
      timeout: requestTimeout,
      tags: { service: target.name, operation: 'readiness' },
    });
    checkedServices.add(1, { service: target.name });
    readinessLatency.add(response.timings.duration, { service: target.name });
    const body = safeJson(response);
    const healthy = response.status === 200 && body?.status === 'UP';
    healthyResponses.add(healthy ? 1 : 0, { service: target.name });
    if (!check(response, {
      [`${target.name} returns HTTP 200`]: (r) => r.status === 200,
      [`${target.name} reports UP`]: () => healthy,
    })) {
      readinessFailures.add(1, { service: target.name, status: String(response.status) });
    }
  }
}

export function handleSummary(data) {
  const summary = {
    schemaVersion: 1,
    test: 'full-stack-readiness-smoke',
    generatedAt: new Date().toISOString(),
    targets: targets.map((target) => target.name),
    offeredIterationsPerSecond: rate,
    durationSeconds,
    metrics: data.metrics,
    notes: [
      'Each iteration checks all local service readiness endpoints.',
      'This is a startup/platform smoke test, not a business-flow or seckill capacity result.',
    ],
  };
  const output = JSON.stringify(summary, null, 2);
  return {
    stdout: `${output}\n`,
    [__ENV.FULL_STACK_SUMMARY_FILE || 'full-stack-readiness-summary.json']: output,
  };
}

function parseTargets(raw) {
  if (!raw) return defaultTargets;
  try {
    const value = JSON.parse(raw);
    if (!Array.isArray(value) || value.length === 0) throw new Error('empty');
    return value.map((target) => {
      if (!target || typeof target.name !== 'string' || typeof target.url !== 'string') throw new Error('shape');
      return { name: target.name, url: target.url.replace(/\/$/, '') };
    });
  } catch (_) {
    throw new Error('FULL_STACK_TARGETS must be a JSON array of {name,url} objects.');
  }
}

function positiveInt(raw, fallback) {
  const value = raw ? Number.parseInt(raw, 10) : fallback;
  if (!Number.isInteger(value) || value < 1) throw new Error('FULL_STACK_RATE and duration must be positive integers.');
  return value;
}

function safeJson(response) {
  try { return response.json(); } catch (_) { return null; }
}
