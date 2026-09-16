# Inventory Service

Inventory Service is the authoritative owner of physical stock, immutable movements, campaign
allocations, and regular-purchase holds. No other service changes Inventory tables directly.

## Capabilities

- administrator stock read, idempotent adjustment, and movement history;
- internal campaign allocate/release/reconcile operations;
- atomic multi-line regular-stock holds for Order;
- idempotent hold confirmation/release through Kafka;
- automatic expiration of abandoned regular holds.

## Regular hold model

Inventory locks requested variants in deterministic order and either commits every line or none.
The same `purchaseRequestId` and canonical fingerprint replays the existing hold; a conflicting
identity/items request is rejected. The service, not Order, assigns the five-minute expiry.

```text
Order HTTP create -> HELD
Payment success -> Kafka ConfirmRegularStockHold -> CONFIRMED
failure/deadline -> Kafka ReleaseRegularStockHold -> RELEASED
no terminal command before TTL -> EXPIRED
```

Each terminal transition publishes an Avro fact through Inventory's outbox.

## Boundaries

- admin HTTP: `/api/v1/admin/inventory/**`;
- Campaign internal HTTP: `/internal/v1/campaign-stock-allocations/**`;
- Order internal HTTP: `POST /internal/v1/regular-stock-holds`;
- Kafka: regular-hold command/event topics and owned DLT.

## Configuration and verification

Important groups include datasource/Liquibase, JWT/internal audience and approved clients,
regular-hold API/TTL/skew/expiry settings, Kafka/Schema Registry, command consumer, event outbox, and
fixture settings. Fixture mode is for controlled tests only.

```powershell
.\mvnw.cmd -pl services/inventory-service -am verify
```

See [`docs/inventory/README.md`](../../docs/inventory/README.md) and the
[internal regular checkout contract](../../specs/049-regular-purchase-checkout/contracts/internal-checkout-http.md).

## Troubleshooting

- `INSUFFICIENT_STOCK`: inspect authoritative availability and active allocations/holds; no partial
  hold should exist.
- Hold remains `HELD`: inspect Order outbox, command topic, Inventory consumer/DLT, and expiry worker.
- Clock-skew rejection: fix node/application time; do not enlarge the hold TTL as a workaround.
