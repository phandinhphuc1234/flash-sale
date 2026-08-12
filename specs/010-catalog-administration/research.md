# Research: Product Catalog Administration

## Decision: Keep Feature 010 product-local and defer Kafka/outbox

Feature 010 will not publish Kafka events, create an outbox table, or synchronize downstream
campaign/flash-sale services. PostgreSQL in product-service remains the source of truth.

**Rationale**: The first administration feature already includes privileged writes, lifecycle,
Variant Money, optimistic locking, idempotency replay, audit evidence, HTTP contract work, and
gateway/product-service security boundaries. Deferring events keeps the slice independently
testable and avoids designing downstream consistency before the local write model is stable.

**Alternatives considered**:

- Lifecycle events only: useful later, but still requires event contracts, outbox, retry, ordering,
  and recovery in the first write feature.
- Events for every material catalog change: too broad for the first admin slice and likely to
  create premature coupling with campaign-service and flashsale-service.

## Decision: Use Clean/Hexagonal boundaries for admin writes

Admin HTTP adapters call application input ports. Application use cases coordinate domain policies
and output ports. Persistence, idempotency, and audit stay in outbound adapters.

**Rationale**: The feature touches privileged write rules and lifecycle invariants; keeping HTTP
DTOs, application commands, domain models, and JPA entities separate prevents the public reader DTOs
from becoming write-domain models.

**Alternatives considered**:

- Controller calls Spring Data repositories directly: faster to write but leaks persistence and
  authorization/error details into HTTP code.
- One global Product DTO/mapper: convenient at first, but couples public catalog reads, admin writes,
  persistence, and future events.

## Decision: Model Product as the aggregate candidate for admin commands

Product is the lifecycle owner for Product content, Variant ownership, Category memberships, media
ownership, publication prerequisites, archived finality, and immutable published identifiers.

**Rationale**: Feature 010 commands change related Product-owned records that must be validated
together and persisted atomically. Product-level optimistic versioning gives a simple concurrency
boundary that matches the existing schema.

**Alternatives considered**:

- Variant as a separate aggregate for price/status: possible later for high-write inventory/price
  domains, but premature for admin catalog maintenance.
- Table-per-use-case procedural updates: easier per endpoint, but makes lifecycle and all-or-nothing
  composition rules harder to enforce consistently.

## Decision: Use optimistic locking with Product expected version

Admin update and lifecycle commands require an expected Product version. Stale versions return a
stable conflict and persist no mutation.

**Rationale**: Feature 008 already has `products.version`, and admin writes are lower volume than
shopper reads. Optimistic locking gives good correctness with little operational complexity and
teaches the intended Spring JPA concurrency model.

**Alternatives considered**:

- Last-write-wins: rejected by spec because it can silently overwrite another operator's update.
- Per-Product database lock for all writes: stronger serialization but unnecessary complexity for
  low-volume admin commands.
- Partial merge: too complex for the first administration slice.

## Decision: Store idempotency replay records for 7 days

Create and lifecycle commands use an idempotency key. The same actor/key/request hash replays the
original outcome for 7 days. The same key with a different request hash is rejected.

**Rationale**: Admin operations are low volume, so 7-day replay is easy to store and much easier to
debug than a very short TTL. It prevents duplicate Product creation and repeated lifecycle effects
when a client retries after a lost response.

**Alternatives considered**:

- 24 hours: smaller storage window, but less helpful for admin debugging and delayed retries.
- Permanent retention: unnecessary and creates avoidable data retention burden.
- No idempotency: rejected by spec because create/lifecycle retry behavior must be deterministic.

## Decision: Add service-owned admin support tables

Feature 010 adds `product_admin_idempotency_keys` and `product_admin_audit_logs` in product-service
Liquibase migrations.

**Rationale**: Existing Product tables support catalog state but not replayable command outcomes or
durable privileged mutation audit evidence. These tables are product-service-owned infrastructure
for Product admin behavior, not shared platform infrastructure.

**Alternatives considered**:

- Store idempotency only in memory: unsafe across restarts and multiple instances.
- Store audit only in logs: useful but weaker for deterministic tests and local audit inspection.
- Root infrastructure audit store: violates service ownership for this initial feature.

## Decision: Require JWT validation at gateway and product-service

Gateway validates `CATALOG_ADMIN` for routing, and product-service revalidates authority before
executing admin use cases.

**Rationale**: Gateway is the public policy edge, but product-service owns the write behavior and
must not trust an unvalidated internal request for privileged mutation. This preserves service
autonomy and prevents accidental bypass if routing changes later.

**Alternatives considered**:

- Gateway-only authorization: simpler, but violates defense-in-depth for privileged writes.
- Trusted internal identity only: weaker for local development and harder to verify end-to-end.
- Product-service login/token issuance: wrong boundary; authentication-service owns tokens.

## Decision: Keep admin contract separate from shopper catalog contract

Admin endpoints use `/api/v1/admin/catalog/**`. Shopper endpoints remain under `/api/v1/catalog/**`.

**Rationale**: Admin responses include non-public lifecycle state, versions, and write errors that
must not leak into the public reader contract. Keeping base paths separate protects Feature 009
compatibility.

**Alternatives considered**:

- Reusing public `/api/v1/catalog/**` with role-sensitive responses: risky because public and admin
  behavior would drift behind the same URL.
- Versioning only through headers: less visible and unnecessary for the first admin contract.
