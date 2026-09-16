# Data Model: Order to Inventory Resilience

## Durable data impact

None. This feature adds no table, column, index, migration, Redis key, Kafka record, HTTP payload, or
business aggregate. Feature 049 PostgreSQL checkpoints remain the durable recovery source.

## Runtime policy model

The following is configuration/state, not domain data.

### InventoryResiliencePolicy

| Field | Type | Default | Validation |
|---|---|---:|---|
| `slidingWindowSize` | integer | 20 | at least 2 |
| `minimumNumberOfCalls` | integer | 10 | 1 through sliding-window size |
| `failureRateThreshold` | percentage | 50 | greater than 0 and at most 100 |
| `waitDurationInOpenState` | duration | 10s | positive |
| `permittedCallsInHalfOpenState` | integer | 2 | at least 1 |
| `automaticTransition` | boolean | false | fixed default, externally configurable |
| `maxConcurrentCalls` | integer | 8 | at least 1 |
| `maxWaitDuration` | duration | 0ms | non-negative; first release defaults to zero |

Suggested environment mappings:

- `ORDER_INVENTORY_RESILIENCE_SLIDING_WINDOW_SIZE`
- `ORDER_INVENTORY_RESILIENCE_MINIMUM_CALLS`
- `ORDER_INVENTORY_RESILIENCE_FAILURE_RATE_THRESHOLD`
- `ORDER_INVENTORY_RESILIENCE_OPEN_WAIT`
- `ORDER_INVENTORY_RESILIENCE_HALF_OPEN_CALLS`
- `ORDER_INVENTORY_RESILIENCE_AUTOMATIC_TRANSITION`
- `ORDER_INVENTORY_RESILIENCE_MAX_CONCURRENT_CALLS`
- `ORDER_INVENTORY_RESILIENCE_MAX_WAIT`

### Circuit state

`CLOSED -> OPEN -> HALF_OPEN -> CLOSED|OPEN`

- `CLOSED`: admitted calls reach Inventory; recorded outcomes update the window.
- `OPEN`: calls fail fast without invoking Inventory.
- `HALF_OPEN`: only the configured probe count may invoke Inventory.
- Restarting an Order replica resets this runtime state but does not alter durable recovery data.

### Outcome classification

| Outcome | Breaker effect | Caller result |
|---|---|---|
| Hold created/replayed | success | existing `RegularStockHold` |
| Insufficient stock | ignored | existing business failure |
| Item not found | ignored | existing business failure |
| Identity conflict | ignored | existing business failure |
| Timeout/connection/ambiguous result | recorded failure | existing recoverable failure |
| Remote unavailable/server failure | recorded failure | existing recoverable failure |
| Breaker open | no delegate call | `INVENTORY_SERVICE_UNAVAILABLE` |
| Bulkhead full | no delegate call | `INVENTORY_SERVICE_UNAVAILABLE` |

## Identity invariant

The decorator must pass through the same `RegularStockHoldCommand` and trace ID. Durable recovery
must continue reusing `holdId`, `purchaseRequestId`, `orderId`, `shopperId`, item IDs, quantities,
and the canonical request fingerprint. Resilience runtime state never generates a business ID.
