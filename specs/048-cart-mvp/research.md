# Research: Authenticated Cart MVP

**Date**: 2026-08-29
**Status**: Complete — no unresolved technical clarification

## Decision 1 — Keep Cart outside purchase and flash-sale admission

**Decision**: Cart owns only authenticated pre-order intent. It does not reserve stock, call Order or
Payment, or become an input dependency of the Redis Lua seckill hot path.

**Rationale**: ADR 0002 assigns mutable pre-order intent to Cart while Product, Inventory, Flash
Sale, Order, and Payment retain their current ownership. This keeps a convenience feature from
adding latency or failure modes to the core seckill path.

**Alternatives considered**:

- Cart-driven checkout: rejected because no checkout behavior is in the approved specification.
- Cart as a required Flash Sale step: rejected because it would weaken the narrow hot path.
- Client-only Cart: rejected by the accepted independent Cart boundary.

## Decision 2 — Persist only variant identity and quantity

**Decision**: Store Cart/owner identity, `variantId`, quantity, and audit timestamps. Never persist a
Cart-owned price, product name, image, stock count, sellability, or Product foreign key.

**Rationale**: Product remains authoritative for catalog and price; Inventory/Flash Sale remain
authoritative for stock. A local snapshot would become stale and require a refresh/retention policy
not approved for this MVP.

**Alternatives considered**:

- Store a price/name snapshot: rejected because it can be mistaken for purchase authority.
- Query Product tables: prohibited cross-service database access.
- Store full Product response JSON: rejected because it transfers Product schema ownership.

## Decision 3 — One batch Product lookup

**Decision**: Add one internal Product batch query accepting unique variant IDs and returning one
ordered result per requested ID. A non-empty Cart read performs exactly one downstream request; a
set-quantity operation queries one variant.

**Rationale**: Q2-B requires current Product information. The existing public Product API is
slug/product-oriented and the Campaign endpoint is validation-specific; neither safely supplies a
variant-ID batch display contract. Batch lookup avoids N+1 network calls and stays within the
one-second read goal.

**Alternatives considered**:

- Call the current Campaign validation endpoint: rejected because it lacks display fields and is
  secured for `campaign-service`.
- Call public product detail once per item: rejected because Cart stores variant IDs and N+1 latency
  grows with Cart size.
- Kafka-maintained Product projection: rejected as excessive consistency and operational scope for
  a seven-day read-only enrichment requirement.

## Decision 4 — Fail-soft reads and fail-closed mutations

**Decision**: A Product dependency failure during GET returns saved intent with
`detailsAvailable=false` and no fabricated Product fields. The same failure during PUT returns a
safe 503 before any Cart mutation. Definitive not-found and not-sellable Product results reject PUT.

**Rationale**: Q5-A explicitly preserves Cart visibility during dependency failure, while FR-007 and
FR-008 require successful sellability verification before mutation.

**Alternatives considered**:

- Fail every read: rejected by Q5-A.
- Accept mutation and validate later: rejected because it violates the approved pre-mutation rule.
- Return cached/default price: rejected because Cart has no approved cache or price ownership.

## Decision 5 — OpenFeign behind a business capability port

**Decision**: Use the managed OpenFeign dependency in `adapter/out/client/product` behind a
`LoadProductDisplaysPort`. Use an OAuth2 authorized-client manager, explicit 300 ms connect and
600 ms read timeouts, BASIC/NONE safe logging, and `Retryer.NEVER_RETRY`.

**Rationale**: Cart needs an immediate blocking answer and the repository already has an accepted
Spring Cloud/OpenFeign/OAuth pattern. The call is a query, short, outside the Gateway event loop and
outside Cart transactions.

**Alternatives considered**:

- Raw `RestClient`: viable but rejected to stay consistent with the existing service-call pattern.
- Automatic retry: rejected because it multiplies latency and the approved failure policy already
  defines graceful read degradation.
- Circuit breaker/Resilience4j: deferred; adding another dependency is not necessary for the MVP.

## Decision 6 — Dedicated Cart service identity and scope

**Decision**: Provision `cart-service` with only `catalog.variant-display.read`; never relay shopper
or administrator tokens. Product requires the internal audience, exact Cart subject, and exact
scope for the new endpoint.

**Rationale**: Least privilege makes the machine boundary demonstrable and prevents Cart from using
Campaign allocation/validation privileges.

**Alternatives considered**:

- Reuse Campaign credentials or `catalog.read`: rejected because it couples identities and grants
  broader access than the new endpoint needs.
- Forward the shopper token: rejected because the downstream action is service capability access,
  not user delegation.

## Decision 7 — PostgreSQL atomic upsert for mutation concurrency

**Decision**: Use Spring Data JPA for normal Cart persistence and a PostgreSQL-specific upsert inside
the persistence adapter for create/find Cart plus set-item quantity. Enforce unique owner and unique
`(cart_id, variant_id)` constraints.

**Rationale**: Idempotent PUT and concurrent first writes otherwise race between select and insert.
Database uniqueness and `ON CONFLICT` provide one durable row and last-committed quantity without
leaking SQL into the application/domain layers.

**Alternatives considered**:

- Distributed lock/Redis: rejected because PostgreSQL already owns Cart truth and Cart is not a hot
  path.
- Pessimistic lock only: cannot lock a Cart row that does not exist during concurrent first writes
  without an additional lock identity.
- Optimistic version only: does not by itself resolve the first-insert uniqueness race.

## Decision 8 — No automatic expiry

**Decision**: Cart rows and items remain until shopper mutation. No scheduled cleanup, retention
window, TTL, or expiry state is added.

**Rationale**: Q6-A makes this an explicit MVP rule. Cleanup semantics can be specified later with
retention and operational evidence.

**Alternatives considered**:

- Inactivity expiry: deferred because duration, user messaging, cleanup, and recovery are not in
  scope.
- Redis TTL: rejected because Cart data is durable and Redis is not the source of truth.

## Decision 9 — Additive migration through a one-off runner

**Decision**: Add service-owned Liquibase tables and run them through a Compose `cart-migration`
process. Keep Liquibase disabled in long-running Cart replicas.

**Rationale**: A separate migration process avoids concurrent startup migration when replicas grow.
The change is additive, repeatable through Liquibase history, and compatible with the pre-feature
Cart shell.

**Alternatives considered**:

- Run migration in every application startup: rejected because multiple replicas can contend.
- Root-owned SQL business schema: rejected by service ownership rules.
- InitContainer: deferred with Kubernetes deployment scope.

## Decision 10 — Local/CI delivery before combined cloud rollout

**Decision**: This feature adds local Compose/Gateway/OpenAPI/smoke support and selective CI coverage.
It does not expand the existing eight-service ECR promotion, cloud Kustomize, or Argo application.

**Rationale**: The user has seven days and wants Cart and Notification built locally before one
deployment pass. Mixing a ninth cloud service into the Cart business PR would add ECR, IAM, secrets,
migration Job, resource sizing, monitoring, and rollback scope before Notification is ready.

**Alternatives considered**:

- Deploy Cart immediately: viable later, but rejected for this time-boxed local-first milestone.
- Add Cart to the existing workflow without manifests: rejected because publishing an unused image
  is not an end-to-end delivery capability.

## Manual Secret Boundary

Implementation will add placeholder documentation for `CART_CLIENT_SECRET`. Before live local smoke
testing, the user must place a strong value in ignored `infra/docker/.env`. Automation may validate
presence by key name but must not print, read back into evidence, commit, or replace its value.
