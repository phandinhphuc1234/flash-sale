# Research: Order to Inventory Resilience

## Decision 1 — Start with Order to Inventory regular-stock hold

**Decision**: Protect `POST /internal/v1/regular-stock-holds` as invoked by Order Service.

**Why**: The call sits before Order acceptance, is authoritative for normal stock, already has
stable idempotency identities and durable recovery, and is vulnerable to outage amplification when
many callers wait for the same unhealthy dependency.

**Alternatives rejected**:

- **Gateway first**: useful for edge shaping, but it cannot distinguish Inventory business
  rejections or protect the durable Order recovery path.
- **Payment/Stripe first**: higher financial risk and more states; inappropriate for the first
  learning slice.
- **All Feign clients**: too broad to classify failures safely and violates the approved bounded
  scope.

## Decision 2 — Native Resilience4j with an explicit port decorator

**Decision**: Use native Resilience4j registries and decorators behind
`CreateRegularStockHoldPort`.

**Why**:

- preserves Clean/Hexagonal boundaries;
- targets exactly one capability rather than every method on a Feign client;
- supports explicit business-versus-infrastructure exception classification;
- makes composition and unit tests deterministic;
- avoids fallback methods and annotation/proxy ambiguity.

**Alternatives rejected**:

- **Global Spring Cloud OpenFeign circuit-breaker switch**: enabling the global property wraps all
  Feign methods, expanding the blast radius beyond this feature.
- **Annotations on the use case**: leaks infrastructure policy into orchestration and relies on
  proxy behavior.
- **Custom hand-written breaker**: duplicates established concurrency/state-machine behavior and
  creates avoidable correctness risk.

## Decision 3 — Circuit breaker plus semaphore bulkhead, without retry

**Decision**: Compose a zero-wait semaphore bulkhead outside the circuit breaker. Keep Feign's
`Retryer.NEVER_RETRY` and the existing 300/800 ms timeouts.

**Why**: The breaker limits repeated calls to a failing dependency; the bulkhead limits concurrent
callers while the dependency is slow; the timeout bounds each admitted call. Durable Feature 049
recovery is the one retry owner and reuses the original business identities.

**Alternatives rejected**:

- **Automatic retry**: a timed-out hold may already have committed, so blind retry amplifies load
  and ambiguity.
- **Thread-pool bulkhead**: adds queues, executors, and context-propagation concerns that are not
  justified for the first synchronous slice.
- **TimeLimiter**: introduces asynchronous cancellation semantics while Feign already has explicit
  network timeouts.
- **RateLimiter**: controls request rate, not dependency health or concurrent blocking; it is not
  required for this incident pattern.

## Decision 4 — Record only infrastructure instability

**Decision**: Business outcomes do not affect the breaker. Ambiguous transport outcomes, remote
unavailability, and unexpected runtime integration failures do.

**Why**: Out-of-stock and not-found responses prove Inventory is reachable and functioning. Counting
them would open the breaker during a popular sale even when the dependency is healthy.

## Decision 5 — In-memory state, readiness independent

**Decision**: Each Order replica owns its local breaker/bulkhead state. Do not persist or coordinate
breaker state and do not include it in readiness.

**Why**: The state describes one process's recent dependency observations, not business truth.
Persisting it adds distributed coordination without improving durable correctness. Kubernetes should
not remove every healthy Order pod merely because Inventory is unavailable.

## Decision 6 — Conservative initial tuning

**Decision**: Start with the defaults documented in `plan.md`, expose every threshold through
validated configuration, keep permit wait fixed at zero, and tune concurrency only from controlled
evidence.

**Why**: A tiny window opens on noise; an oversized window reacts too slowly. A zero-wait bulkhead
prevents a second local queue, while a small probe count limits pressure during recovery.

The initial concurrency limit is 16 per Order replica. The available 100 RPS Flash Sale result and
the 32-thread Inventory correctness fixture bracket a useful starting range, but neither is a direct
regular-purchase dependency benchmark. Sixteen therefore provides headroom over the observed local
healthy-path concurrency without adopting the test-only 32-thread ceiling as a production default.

## Dependency compatibility evidence

- Root dependency management imports Spring Cloud `2025.0.3`.
- The locally resolved Spring Cloud CircuitBreaker dependency management line is `3.3.3` and
  manages Resilience4j `2.2.0`.
- Implementation must capture the effective dependency tree rather than assuming this research
  snapshot remains current.

## Open research items for implementation tasks

No business requirement is unresolved. Implementation tasks must verify only technical facts that
can drift: the effective managed version, exact exported metric names, and the concurrency default
under deterministic local load. A failed compatibility check stops implementation and triggers a
plan update; it does not authorize an ad-hoc version override.
