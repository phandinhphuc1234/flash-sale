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
