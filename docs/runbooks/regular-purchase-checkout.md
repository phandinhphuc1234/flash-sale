# Regular Purchase Checkout runbook

This runbook covers the normal Buy Now and Cart checkout path owned by Order, Inventory, Cart,
and Payment. It applies to local Docker Compose first and to the cloud release only after the
reviewed migration/image/config gates pass.

## Safety rules

- PostgreSQL is the durable source of truth. Do not repair a checkout by deleting business rows,
  changing stock with SQL, or manually marking an Order/Payment successful.
- Use the same `Idempotency-Key` and request body when replaying a client request. A new key can
  create a new business intent.
- Do not copy access tokens, refresh cookies, Stripe keys/signatures, card data, raw webhook
  bodies, Checkout URLs, or high-cardinality IDs into logs, tickets, dashboards, or chat.
- Disable regular intake before a release rollback. Preserve rows and let the recovery workers
  converge durable work.

## Identify the failing stage

1. Check the Order, Inventory, Cart, and Payment readiness endpoints and the Gateway route.
2. Correlate the request with the redacted trace ID and safe aggregate status. The metrics use
   bounded labels such as `source`, `outcome`, `stage`, and Saga state; they intentionally do not
   contain shopper, Order, Cart, variant, provider, or token values.
3. Check the `order_regular_purchase_intake_total`,
   `inventory_regular_hold_state_total`, `cart_reconciliation_outcome_total`, and Payment recovery
   metrics before inspecting logs.

## Rejected checkout before a hold

For `CART_CHANGED`, `PRICE_CHANGED`, `CART_VARIANT_NOT_SELLABLE`, `INSUFFICIENT_STOCK`, or an
invalid request, show the actionable error to the shopper and do not retry with a new key. Reload
the Cart/Product state and ask the shopper to submit a fresh intent. No Order or Payment should
exist for a rejected intake.

## Hold acquired but checkout does not complete

Inventory owns the five-minute regular hold. Check the hold state and expiry-age metrics, then
verify the Order regular-purchase recovery worker. A transient dependency failure should leave a
reclaimable lease/outbox item; do not create another hold manually. If the hold expires, the Order
must converge to its approved expired/cancelled boundary and Inventory must release stock once.

## Payment failure or expiry

Payment terminal failure emits the versioned release command. Verify the Order Saga transition,
the Inventory released/expired fact, and the Cart reconciliation boundary. Replays must be
idempotent. If a release fact conflicts with the active hold identity, preserve it for the owning
DLT/manual-review workflow instead of forcing a state change.

## Replay and DLT handling

- A duplicate Buy Now/Cart request with the same key and fingerprint should return the stored
  accepted/rejected result without a second Order or hold.
- Malformed or identity-conflicting Kafka records are acknowledged only through the consumer's
  bounded DLT policy. Transient database/broker failures remain retryable.
- Before replaying a DLT record, verify the contract version, aggregate identity, causation ID,
  and current durable state. Use the service-owned replay procedure; never edit the payload in
  place.

## Cart reconciliation and orphan holds

After confirmed payment, Cart removes only lines whose variant, quantity, and item revision still
match the submitted snapshot. A `partial_noop`/`conflict` result means a later shopper edit was
preserved, not that the purchase failed. An apparently orphaned hold is investigated through the
Order hold identity and outbox/inbox evidence; do not decrement or increment stock directly.

## Manual review and late success

Payment success is dominant. If success arrives after a local deadline or after a release race,
the Saga may enter `MANUAL_REVIEW`. Stop automatic retries, retain provider/aggregate identities,
and escalate with only redacted status evidence. Refunds, compensation, and direct provider calls
are outside this MVP unless an approved business decision adds them.

## Dependency restart rehearsal

The local smoke runner will not restart a service unless invoked with its explicit dependency-
restart opt-in. Before using it, confirm the Compose stack is disposable, regular intake is
disabled, and no unrelated scenario is running. After restart, run the recovery/replay tests and
verify outbox/inbox counts and hold states converge. Never run the restart scenario against cloud
or a shared database.

## Closure evidence

Record the command, exit code, bounded metric categories, dependency status, and test/PR reference.
Redact all secrets, Authorization headers, cookies, signatures, card/customer data, raw payloads,
provider URLs, Checkout URLs, and unbounded identifiers before committing evidence.
