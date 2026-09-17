# Order Service

Order Service owns Orders and orchestrates purchase Sagas. It accepts flash-sale purchase facts and
normal Buy Now/Cart checkout requests, but it does not own Product pricing, Inventory stock, or
provider payment state.

## Entry points

| Flow | Entry |
|---|---|
| Read Orders | `GET /api/v1/orders` and `/api/v1/orders/{orderId}` |
| Normal Cart | `POST /api/v1/orders/cart-checkouts` |
| Normal Buy Now | `POST /api/v1/orders/buy-now` |
| Flash Sale | consume `PurchaseAcceptedV1` |

Normal purchase intake uses a five-second public budget, at most 20 distinct lines, authoritative
Product quotes, and an atomic Inventory hold. Cart checkout also validates the internal Cart
snapshot/version. A durable intake state lets recovery resume ambiguous downstream calls with the
same identities.

## Saga coordination

```text
accepted purchase -> Order PENDING_PAYMENT + Saga
                 -> PaymentRequested
PaymentSucceeded -> confirm Flash Sale reservation or regular Inventory hold
                 -> terminal confirmation fact
                 -> Order CONFIRMED
PaymentFailed/deadline -> release reservation/hold -> CANCELLED or EXPIRED
late verified success -> bounded correction/manual-review rule; never invent refund behavior
```

For paid Cart checkout, Order emits `ReconcilePurchasedCartSnapshotV1` only after confirmation.

## Reliability and data

Order owns `order_db`, purchase intake, Saga state, inbox/idempotency records, and transactional
outbox. Consumer retries are bounded and each boundary has an owned DLT. Schedulers claim work with
leases so a crashed replica does not permanently strand recovery.

## Order-to-Inventory resilience

The regular Buy Now/Cart hold call is protected locally in each Order pod. The protection is
deliberately narrow and does not change the Inventory HTTP contract or the durable purchase
identity:

```text
regular purchase intake
  -> semaphore Bulkhead (max 16 per Order pod, wait 0 ms)
  -> Circuit Breaker (20-call window, open after 10 calls at >=50% failures)
  -> Inventory regular-hold client (300 ms connect / 800 ms read, no retry)
```

When the bulkhead is full or the breaker is open, the call fails immediately with the existing
recoverable `INVENTORY_SERVICE_UNAVAILABLE` outcome. Inventory is not invoked and no fallback
stock or duplicate hold is created. Business rejections (`INSUFFICIENT_STOCK`, `ITEM_NOT_FOUND`,
and `HOLD_CONFLICT`) remain truthful and are ignored by the breaker. Ambiguous and unexpected
remote failures are recorded and are retried only by the existing durable recovery path, with the
same hold/order/purchase identities.

### Runtime controls and operator signals

The policy is configured under `order.regular-purchase.inventory-resilience` in `application.yml`.
The `ORDER_INVENTORY_RESILIENCE_*` environment variables override the window, thresholds, cooling
interval, half-open probe count, automatic transition, and per-pod concurrent-call limit. Zero
bulkhead wait is fixed in code and is not configurable.

Resilience4j Micrometer metrics are exported through the existing `/actuator/prometheus` endpoint.
Use the fixed `name`, `kind`, and `state` dimensions on `resilience4j.circuitbreaker.*` metrics,
plus the named bulkhead available/max gauges. `CLOSED` means normal admission, `OPEN` means
temporary isolation after remote failures, and `HALF_OPEN` means bounded recovery probes. Logs
emit fixed transition and rejection outcomes (`open_circuit` or `bulkhead_full`) with a normalized
trace ID.

Order readiness depends on PostgreSQL and its own readiness indicator only. Inventory outage,
breaker state, and bulkhead saturation are diagnostic signals and must not remove Order from
service. Never add shopper, order, hold, purchase-request, token, request-body, or raw-URL values
to metric labels or resilience logs.

Tune `max-concurrent-calls` only after measuring the regular Order-to-Inventory call for the target
pod CPU and timeout budget. Keep the network timeouts and no-blind-retry rule unchanged; do not
add a fallback, TimeLimiter, RateLimiter, or a second shared resilience abstraction in this slice.

## Configuration and verification

Configuration groups cover datasource/Liquibase; shopper and internal JWT trust; Product, Cart,
Inventory, and OAuth URLs/timeouts; all Kafka topics/groups/DLTs; outbox; public query/intake flags;
regular hold/deadline policy; and recovery leases/backoff.

```powershell
.\mvnw.cmd -pl services/order-service -am verify
pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 -Scenario All
```

## Troubleshooting

- Order stuck in `PENDING_PAYMENT`: follow Order outbox -> Payment consumer -> Payment event -> Order
  consumer, then inspect DLTs.
- Normal intake times out: a timeout is ambiguous; resume the durable request with the same IDs
  instead of creating a new purchase.
- Final stock state lags Payment: inspect the correct reservation/hold command and result topic; do
  not patch Order status directly.
