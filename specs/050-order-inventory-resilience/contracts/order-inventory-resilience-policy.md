# Contract: Order to Inventory Resilience Policy

## Protected capability

- Caller: Order Service
- Callee: Inventory Service
- Existing operation: `POST /internal/v1/regular-stock-holds`
- Existing application port: `CreateRegularStockHoldPort#create`

The HTTP method, path, headers, request body, response envelope, OAuth scope, trace propagation, and
stable error codes are unchanged.

## Admission contract

1. A request must acquire a semaphore-bulkhead permit without waiting.
2. If no permit is available, Inventory is not invoked and Order emits the existing recoverable
   `INVENTORY_SERVICE_UNAVAILABLE` outcome.
3. If admitted, the circuit breaker decides whether the delegate may run.
4. If the breaker is open, Inventory is not invoked and the same recoverable outcome is emitted.
5. If admitted by both controls, the existing client adapter performs the OpenFeign call with the
   existing timeouts and no retry.

## Failure-classification contract

The protection policy ignores these valid business outcomes:

- `INVENTORY_INSUFFICIENT_STOCK`
- `INVENTORY_ITEM_NOT_FOUND`
- `INVENTORY_HOLD_CONFLICT`

The policy records these as dependency failures:

- `INVENTORY_HOLD_AMBIGUOUS`
- `INVENTORY_SERVICE_UNAVAILABLE`
- unexpected runtime failures from the outbound integration boundary

## Recovery contract

- The breaker permits only the configured number of half-open probes.
- A successful probe contributes to closing the breaker.
- A recorded failed probe returns the breaker to open.
- Business rejections remain ignored during half-open evaluation.
- Feature 049 durable recovery remains responsible for retry timing and for reusing the original
  business identities.

## Observability contract

- Metrics use fixed instance names and bounded state/outcome labels.
- Logs may contain the normalized trace ID and fixed failure/state names.
- Metrics/logs must not contain shopper IDs, order IDs, hold IDs, purchase-request IDs, access
  tokens, client secrets, request bodies, or raw URLs.
- Breaker state is not part of Order readiness.

## Compatibility contract

Protected and prior Order images must remain compatible with the same Inventory HTTP contract and
database state. Deployment and rollback therefore require neither a data migration nor a coordinated
Inventory release.
