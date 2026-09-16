import http from 'k6/http';
import { sleep } from 'k6';
import exec from 'k6/execution';
import encoding from 'k6/encoding';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { acceptedReservation, ownedOrderId, matchingOrder, stageReport,
  failureCodes, failureCode, validateReservationWindow } from './contracts.mjs';

// Invoke through the PowerShell runner: it owns fixture budgets and the explicit live opt-in.
if (__ENV.FSO_RUN !== '1') throw new Error('Use run-flash-sale-to-order-stress.ps1 -Run.');
const cfg = JSON.parse(__ENV.FSO_SETTINGS);
const tokens = new SharedArray('flash-sale-to-order shoppers', () => JSON.parse(open(__ENV.FSO_TOKEN_FILE)));
const baseUrl = cfg.baseUrl.replace(/\/$/, '');
const names = ['arrivals', 'completed', 'submitted', 'accepted', 'observed', 'reads',
  'replay_requests', 'replays', 'sold_out', 'unresolved', 'errors', 'submitted_in_window', 'observed_in_window',
  ...failureCodes.map(code => `failure_${code}`)];
const counters = Object.fromEntries(names.map(name => [name, new Counter(`fs_order_${name}`)]));
const admission = new Trend('fs_order_admission_ms', true);
const replayLatency = new Trend('fs_order_replay_ms', true);
const visibility = new Trend('fs_order_visibility_ms', true);
const submitOffset = new Trend('fs_order_submit_offset_ms', true);
const visibleOffset = new Trend('fs_order_visible_offset_ms', true);

export const options = {
  scenarios: { flash_sale_to_order: {
    executor: 'constant-arrival-rate', rate: cfg.rate, timeUnit: '1s', duration: `${cfg.duration}s`,
    preAllocatedVUs: cfg.vus, maxVUs: cfg.vus,
    // Preserve in-flight observations; the parent still enforces a hard process deadline.
    gracefulStop: `${cfg.observeSeconds + cfg.requestSeconds * 3 + cfg.settleSeconds + 5}s`,
  } },
  summaryTrendStats: ['min', 'max', 'avg', 'p(95)', 'p(99)'],
  // Drop URL/owner/trace tags to avoid high-cardinality or sensitive raw result data.
  systemTags: ['name', 'method', 'status', 'scenario', 'expected_response'],
  maxRedirects: 0,
  thresholds: {
    fs_order_errors: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '1s' }],
    fs_order_unresolved: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '1s' }],
    dropped_iterations: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '1s' }],
    fs_order_accepted: [{ threshold: `count<=${cfg.allocation}`, abortOnFail: true }],
  },
};

function json(response) {
  try { return response.json(); } catch { throw new Error('non_json_response'); }
}
function get(path, token, operation, timeoutSeconds = cfg.requestSeconds) {
  counters.reads.add(1);
  const response = http.get(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}` }, timeout: `${timeoutSeconds}s`, redirects: 0,
    tags: { name: operation },
  });
  if (response.status !== 200) throw new Error('observer_http_error');
  return json(response);
}
function orderId(token, timeoutSeconds) {
  return ownedOrderId(get('/api/v1/orders?page=0&size=2', token, 'order_list', timeoutSeconds));
}
function post(token, key, isReplay) {
  const response = http.post(`${baseUrl}/api/v1/flash-sales/${cfg.campaignId}/reservations`,
    JSON.stringify({ variantId: cfg.variantId, quantity: 1 }), {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
        'Idempotency-Key': key }, timeout: `${cfg.requestSeconds}s`, redirects: 0,
      tags: { name: isReplay ? 'reservation_replay' : 'reservation_new' },
    });
  (isReplay ? replayLatency : admission).add(response.timings.duration);
  return response;
}

export default function () {
  // Explicit zeros let summaries distinguish a real zero from an iteration never executed.
  for (const name of names) counters[name].add(0);
  counters.arrivals.add(1);
  try {
    const index = cfg.tokenOffset + exec.scenario.iterationInTest;
    const token = tokens[index];
    if (!token) throw new Error('insufficient_shoppers');
    const claims = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's'));
    if (claims.exp * 1000 < Date.now() + (cfg.observeSeconds + 30) * 1000) {
      throw new Error('shopper_token_expiring');
    }
    // Fresh owner makes list+detail correlation bounded and detects extra Orders, not just one match.
    if (orderId(token) !== null) throw new Error('shopper_not_fresh');
    const key = `fso-${cfg.runId}-${index}`;
    const startedAt = Date.now();
    const offset = startedAt - exec.scenario.startTime;
    submitOffset.add(offset);
    counters.submitted.add(1);
    if (offset < cfg.duration * 1000) counters.submitted_in_window.add(1);
    const first = post(token, key, false);
    if (first.status === 0 || first.status === 503) {
      counters.unresolved.add(1); // A timeout/pending response may already have committed. Do not retry blindly.
      return;
    }
    const body = json(first);
    if (cfg.scenario === 'SoldOut' && first.status === 409 && body.errorCode === 'FLASH_SALE_SOLD_OUT') {
      counters.sold_out.add(1);
      sleep(cfg.settleSeconds);
      if (orderId(token) !== null) throw new Error('rejected_purchase_created_order');
      counters.completed.add(1);
      return;
    }
    if (first.status !== 202) throw new Error('unexpected_admission_response');
    const accepted = acceptedReservation(body, cfg);
    counters.accepted.add(1);
    // If holds expire during the ladder, released quota can be reused: a cumulative allocation
    // comparison would no longer be valid. Reject the fixture, never extend the business TTL.
    validateReservationWindow(accepted, cfg.runDeadlineEpochMs);
    if (cfg.scenario === 'Replay') {
      counters.replay_requests.add(1);
      const replay = post(token, key, true);
      if (replay.status !== 202) throw new Error('unexpected_replay_response');
      acceptedReservation(json(replay), accepted);
      counters.replays.add(1);
    }

    const deadline = startedAt + cfg.observeSeconds * 1000;
    let found = null, foundAt = null;
    while (Date.now() < deadline) {
      const remaining = () => Math.max(0.001, Math.min(cfg.requestSeconds, (deadline - Date.now()) / 1000));
      const id = orderId(token, remaining());
      if (id && Date.now() < deadline) {
        matchingOrder(get(`/api/v1/orders/${id}`, token, 'order_detail', remaining()), accepted, id);
        if (Date.now() < deadline) { found = id; foundAt = Date.now(); break; }
      }
      sleep(Math.max(0, Math.min(cfg.pollMs, deadline - Date.now())) / 1000);
    }
    if (!found) { counters.unresolved.add(1); return; }
    // Delay is for a bounded duplicate observation, not for pacing constant-arrival-rate traffic.
    sleep(cfg.settleSeconds);
    if (orderId(token) !== found) throw new Error('order_identity_changed');
    matchingOrder(get(`/api/v1/orders/${found}`, token, 'order_recheck'), accepted, found);
    visibility.add(foundAt - startedAt);
    visibleOffset.add(foundAt - exec.scenario.startTime);
    counters.observed.add(1);
    if (foundAt - exec.scenario.startTime < cfg.duration * 1000) counters.observed_in_window.add(1);
    counters.completed.add(1);
  } catch (error) {
    counters.errors.add(1); // Deliberately omit arbitrary exception text that could contain credentials.
    counters[`failure_${failureCode(error)}`].add(1);
  }
}

export function handleSummary(data) {
  const report = stageReport(data, cfg);
  return { [__ENV.FSO_SUMMARY_FILE]: JSON.stringify(report, null, 2),
    stdout: `FLASH_SALE_TO_ORDER_STAGE=${report.outcome.toUpperCase()}\n` };
}
