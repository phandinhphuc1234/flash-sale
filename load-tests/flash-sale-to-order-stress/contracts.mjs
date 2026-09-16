// Pure HTTP contract/report functions. Both k6 and offline Node tests use this code.
function requireThat(condition, code) {
  if (!condition) throw new Error(code); // Never embed response bodies or credentials in errors.
}
function payload(body) {
  requireThat(body?.success === true && body.data && typeof body.data === 'object', 'invalid_envelope');
  return body.data;
}
const identity = (value) => typeof value === 'string' && value.length > 0;

export const failureCodes = ['invalid_envelope', 'missing_reservation_identity', 'reservation_identity_mismatch',
  'invalid_reservation_state', 'invalid_order_page', 'owner_not_unique', 'inconsistent_order_page',
  'missing_order_identity', 'order_identity_mismatch', 'order_correlation_mismatch', 'invalid_order_state',
  'order_line_mismatch', 'non_json_response', 'observer_http_error', 'insufficient_shoppers',
  'shopper_token_expiring', 'shopper_not_fresh', 'rejected_purchase_created_order',
  'unexpected_admission_response', 'unexpected_replay_response', 'order_identity_changed',
  'reservation_window_too_short', 'unexpected_error'];

export function failureCode(error) {
  return failureCodes.includes(error?.message) ? error.message : 'unexpected_error';
}

export function validateReservationWindow(accepted, deadlineEpochMs) {
  requireThat(Number.isFinite(Date.parse(accepted.expiresAt)) && Date.parse(accepted.expiresAt) > deadlineEpochMs,
    'reservation_window_too_short');
}

export function acceptedReservation(body, expected) {
  const data = payload(body);
  for (const field of ['purchaseRequestId', 'reservationId', 'campaignId', 'variantId']) {
    requireThat(identity(data[field]), 'missing_reservation_identity');
    if (expected[field]) requireThat(data[field] === expected[field], 'reservation_identity_mismatch');
  }
  requireThat(data.quantity === 1 && data.status === 'RESERVED', 'invalid_reservation_state');
  return data;
}

export function ownedOrderId(body) {
  const data = payload(body);
  requireThat(Array.isArray(data.data) && data.page, 'invalid_order_page');
  const page = data.page;
  requireThat(Number.isInteger(page.totalElements) && page.totalElements >= 0 &&
    Number.isInteger(page.totalPages) && page.totalPages >= 0 && page.number === 0, 'invalid_order_page');
  requireThat(page.totalElements <= 1 && page.totalPages <= 1 && page.hasNext === false,
    'owner_not_unique');
  requireThat(data.data.length === page.totalElements, 'inconsistent_order_page');
  if (page.totalElements === 0) return null;
  requireThat(identity(data.data[0].id), 'missing_order_identity');
  return data.data[0].id;
}

export function matchingOrder(body, expected, id) {
  const data = payload(body);
  requireThat(data.id === id, 'order_identity_mismatch');
  for (const field of ['purchaseRequestId', 'reservationId', 'campaignId']) {
    requireThat(identity(data[field]) && data[field] === expected[field], 'order_correlation_mismatch');
  }
  requireThat(data.purchaseSource === 'FLASH_SALE' && data.status === 'PENDING_PAYMENT', 'invalid_order_state');
  requireThat(Array.isArray(data.items) && data.items.length === 1 &&
    data.items[0].variantId === expected.variantId && data.items[0].quantity === 1, 'order_line_mismatch');
  return data.id;
}

// No generic spread of k6 metadata: only explicitly allowed numeric fields reach disk.
export function stageReport(data, settings) {
  const metrics = data.metrics || {};
  const numeric = (key, stat, fallback = null) => {
    const v = metrics[key]?.values?.[stat];
    return Number.isFinite(v) ? v : fallback;
  };
  const count = (key) => numeric(`fs_order_${key}`, 'count', 0);
  const arrivals = count('arrivals'), submitted = count('submitted'), accepted = count('accepted');
  const observed = count('observed'), completed = count('completed'), soldOut = count('sold_out');
  const errors = count('errors'), unresolved = count('unresolved'), replays = count('replays');
  const drops = numeric('dropped_iterations', 'count', 0);
  const admissionP95Ms = numeric('fs_order_admission_ms', 'p(95)');
  const admissionP99Ms = numeric('fs_order_admission_ms', 'p(99)');
  const orderVisibleP95Ms = numeric('fs_order_visibility_ms', 'p(95)');
  const orderVisibleP99Ms = numeric('fs_order_visibility_ms', 'p(99)');
  const firstSubmit = numeric('fs_order_submit_offset_ms', 'min');
  const lastVisible = numeric('fs_order_visible_offset_ms', 'max');
  const danger = [], breaches = [];
  if (!arrivals || !submitted || !accepted || !observed) danger.push('no_complete_purchase_evidence');
  if (arrivals !== completed || submitted !== accepted + soldOut) danger.push('incomplete_work');
  if (errors || unresolved) danger.push('unexpected_or_unresolved_outcomes');
  if (drops) danger.push('generator_dropped_iterations');
  if (accepted !== observed) danger.push('accepted_order_count_mismatch');
  if (accepted > settings.allocation) danger.push('allocation_exceeded');
  if (settings.scenario === 'Replay' && replays !== accepted) danger.push('replay_count_mismatch');
  if (settings.scenario === 'SoldOut' ? !soldOut : soldOut > 0) danger.push('scenario_fixture_mismatch');
  if ([admissionP95Ms, admissionP99Ms, orderVisibleP95Ms, firstSubmit, lastVisible].some(v => v === null)) {
    danger.push('missing_measurement');
  }
  if (admissionP95Ms >= settings.admissionP95) breaches.push('admission_p95');
  if (admissionP99Ms >= settings.admissionP99) breaches.push('admission_p99');
  if (orderVisibleP95Ms >= settings.orderP95) breaches.push('order_visibility_p95');
  return {
    schemaVersion: 1, scenario: settings.scenario, offeredArrivalsPerSecond: settings.rate,
    arrivalWindowSeconds: settings.duration, arrivals, completedIterations: completed,
    firstReservationRequests: submitted, acceptedUniqueReservations: accepted, observedOrders: observed,
    replayRequests: count('replay_requests'), verifiedReplays: replays, soldOut,
    observerReadRequests: count('reads'), unresolvedOutcomes: unresolved, errors, droppedIterations: drops,
    actualAdmissionRps: count('submitted_in_window') / settings.duration,
    observedOrdersInWindowRps: count('observed_in_window') / settings.duration,
    observedCompletionRps: lastVisible > firstSubmit ? observed * 1000 / (lastVisible - firstSubmit) : null,
    admissionP95Ms, admissionP99Ms, orderVisibleP95Ms, orderVisibleP99Ms,
    replayP95Ms: numeric('fs_order_replay_ms', 'p(95)'),
    observationTailMs: lastVisible === null ? null : Math.max(0, lastVisible - settings.duration * 1000),
    kafkaLag: null, databaseCommitLatencyMs: null,
    outcome: danger.length ? 'danger' : breaches.length ? 'breach' : 'pass',
    dangerReasons: danger, breachReasons: breaches,
    failureCounts: Object.fromEntries(failureCodes.map(code => [code, count(`failure_${code}`)]).filter(([, n]) => n > 0)),
  };
}
