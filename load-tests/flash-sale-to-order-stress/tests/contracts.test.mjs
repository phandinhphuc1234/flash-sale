import test from 'node:test';
import assert from 'node:assert/strict';
import { acceptedReservation, ownedOrderId, matchingOrder, stageReport,
  failureCode, validateReservationWindow } from '../contracts.mjs';

const ids = { campaignId: 'a', variantId: 'b', purchaseRequestId: 'c', reservationId: 'd' };
const accepted = { success: true, data: { ...ids, quantity: 1, status: 'RESERVED' } };
const detail = { success: true, data: {
  ...ids, id: 'order', status: 'PENDING_PAYMENT', purchaseSource: 'FLASH_SALE',
  items: [{ variantId: 'b', quantity: 1 }],
} };
const list = (n) => ({ success: true, data: {
  data: n ? [{ id: 'order' }] : [],
  page: { number: 0, totalElements: n, totalPages: n ? 1 : 0, hasNext: false },
} });

test('202 requires a successful envelope, complete identities and quantity one', () => {
  assert.equal(acceptedReservation(accepted, ids).reservationId, 'd');
  for (const field of Object.keys(ids)) {
    assert.throws(() => acceptedReservation({ ...accepted, data: { ...accepted.data, [field]: null } }, ids));
  }
  assert.throws(() => acceptedReservation({ ...accepted, success: false }, ids));
  assert.throws(() => acceptedReservation({ ...accepted, data: { ...accepted.data, quantity: 2 } }, ids));
});

test('Order list parses the actual nested PageResponse, not data.content', () => {
  assert.equal(ownedOrderId(list(0)), null);
  assert.equal(ownedOrderId(list(1)), 'order');
  assert.throws(() => ownedOrderId({ success: true, data: { content: [] } }));
});

test('extra Orders, hidden pages and inconsistent pagination cannot look unique', () => {
  assert.throws(() => ownedOrderId(list(2)), /owner_not_unique/);
  assert.throws(() => ownedOrderId({ ...list(1), data: { ...list(1).data,
    page: { ...list(1).data.page, hasNext: true } } }));
  assert.throws(() => ownedOrderId({ ...list(1), data: { ...list(1).data, data: [] } }));
});

test('Order detail must correlate purchase, reservation, campaign, variant, source and quantity', () => {
  assert.equal(matchingOrder(detail, ids, 'order'), 'order');
  for (const field of ['purchaseRequestId', 'reservationId', 'campaignId']) {
    assert.throws(() => matchingOrder({ ...detail, data: { ...detail.data, [field]: 'wrong' } }, ids, 'order'));
  }
  for (const item of [{ variantId: 'wrong', quantity: 1 }, { variantId: 'b', quantity: 2 }]) {
    assert.throws(() => matchingOrder({ ...detail, data: { ...detail.data, items: [item] } }, ids, 'order'));
  }
  assert.throws(() => matchingOrder({ ...detail, data: { ...detail.data, purchaseSource: 'REGULAR' } }, ids, 'order'));
  assert.throws(() => matchingOrder({ ...detail, data: { ...detail.data, status: 'EXPIRED' } }, ids, 'order'));
});

const settings = { rate: 1, duration: 10, scenario: 'NewOrders', allocation: 50,
  admissionP95: 300, admissionP99: 700, orderP95: 5000 };
function summary(overrides = {}) {
  const values = { arrivals: 10, completed: 10, submitted: 10, accepted: 10, observed: 10,
    submitted_in_window: 10, observed_in_window: 8, reads: 35, replays: 0,
    unresolved: 0, errors: 0, sold_out: 0, ...overrides };
  const metrics = Object.fromEntries(Object.entries(values).map(([k, count]) => [`fs_order_${k}`, { values: { count } }]));
  metrics.fs_order_admission_ms = { values: { 'p(95)': 100, 'p(99)': 150 } };
  metrics.fs_order_visibility_ms = { values: { 'p(95)': 1500, 'p(99)': 2000 } };
  metrics.fs_order_visible_offset_ms = { values: { max: 11500 } };
  metrics.fs_order_submit_offset_ms = { values: { min: 100 } };
  return { metrics };
}

test('observer and replay HTTP traffic never inflate new Order throughput', () => {
  const result = stageReport(summary({ reads: 500, replays: 10 }), { ...settings, scenario: 'Replay' });
  assert.equal(result.outcome, 'pass');
  assert.equal(result.actualAdmissionRps, 1);
  assert.equal(result.observedOrdersInWindowRps, 0.8);
  assert.equal(result.observedOrders, 10);
  assert.equal(result.observationTailMs, 1500);
  assert.equal(result.kafkaLag, null);
  assert.ok(result.observedCompletionRps < 1);
});

test('sold-out throughput is not new Order throughput and needs contention', () => {
  const result = stageReport(summary({ accepted: 2, observed: 2, sold_out: 8 }), { ...settings, scenario: 'SoldOut' });
  assert.equal(result.outcome, 'pass');
  assert.equal(result.observedOrders, 2);
  assert.equal(stageReport(summary(), { ...settings, scenario: 'SoldOut' }).outcome, 'danger');
});

test('pending acceptance, missing Orders, interrupted work, oversell and drops fail closed', () => {
  for (const bad of [{ unresolved: 1 }, { observed: 9 }, { completed: 9 }, { errors: 1 }]) {
    assert.equal(stageReport(summary(bad), settings).outcome, 'danger');
  }
  assert.equal(stageReport(summary(), { ...settings, allocation: 9 }).outcome, 'danger');
  const dropped = summary();
  dropped.metrics.dropped_iterations = { values: { count: 1 } };
  assert.equal(stageReport(dropped, settings).outcome, 'danger');
  assert.equal(stageReport({ metrics: {} }, settings).outcome, 'danger');
  assert.equal(stageReport(summary({ replays: 9 }), { ...settings, scenario: 'Replay' }).outcome, 'danger');
});

test('missing latency cannot pass and measured latency breach is distinguished from correctness', () => {
  const slow = summary();
  slow.metrics.fs_order_visibility_ms.values['p(95)'] = 6000;
  assert.equal(stageReport(slow, settings).outcome, 'breach');
  delete slow.metrics.fs_order_admission_ms;
  assert.equal(stageReport(slow, settings).outcome, 'danger');
});

test('report allowlist excludes raw responses, identities, tokens and metric tags', () => {
  const data = summary();
  data.secret = 'NEVER_INCLUDE';
  data.metrics.raw_body = { values: { text: 'NEVER_INCLUDE' } };
  const serialized = JSON.stringify(stageReport(data, { ...settings, token: 'NEVER_INCLUDE' }));
  assert.ok(!serialized.includes('NEVER_INCLUDE'));
  assert.equal(failureCode(new Error('NEVER_INCLUDE')), 'unexpected_error');
});

test('hold expiration before the ladder ends invalidates allocation comparisons', () => {
  validateReservationWindow({ expiresAt: '2030-01-01T00:00:00Z' }, Date.parse('2029-01-01'));
  assert.throws(() => validateReservationWindow({ expiresAt: '2029-01-01' }, Date.parse('2030-01-01')),
    /reservation_window_too_short/);
  assert.throws(() => validateReservationWindow({}, Date.now()));
});
