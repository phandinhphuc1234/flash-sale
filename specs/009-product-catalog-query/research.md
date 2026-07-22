# Research: Product Catalog Query

## Decision: Use Spring Data JPA in product-service

**Rationale**: The feature is a read-heavy catalog slice and the user wants to learn Spring JPA optimization. Spring Data JPA fits the existing Spring Boot service stack, keeps persistence inside product-service, and supports projection queries for bounded list/detail reads without exposing JPA entities to web contracts.

**Alternatives considered**:

- `JdbcTemplate`: simpler SQL control, but misses the learning goal and bypasses the planned JPA query layer.
- Direct repository entities returned from controllers: faster to write, but violates the boundary between persistence adapter and public contract.

## Decision: Use projection queries plus fixed follow-up queries

**Rationale**: Product list/detail needs products plus active variants, categories, and media. One large multi-collection join can duplicate rows and make pagination fragile. The plan pages product ids first, then loads related variants/categories/media in bounded follow-up queries and assembles DTOs in the adapter/application boundary.

**Alternatives considered**:

- Entity graph with multiple collections: convenient but risks row explosion and unstable paging.
- Lazy loading from controllers: simple initially but creates N+1 risk and leaks persistence behavior beyond the adapter.

## Decision: No new database migration

**Rationale**: Feature 008 already created the catalog schema and indexes needed for initial reads. This feature only adds application query behavior. Adding indexes without measured evidence would be premature.

**Alternatives considered**:

- Add read-specific indexes now: deferred until query plans or load tests show a concrete need.

## Decision: Public shopper HTTP endpoints under `/api/v1/catalog`

**Rationale**: The spec requires public shopper access through api-gateway. A catalog prefix separates shopper-facing read APIs from future admin product management APIs.

**Alternatives considered**:

- `/api/v1/products`: shorter, but more likely to collide with future admin/product management semantics.
- Internal-only service routes: rejected by clarified requirement Q1.

## Decision: Gateway route by declarative configuration

**Rationale**: The gateway can route `/api/v1/catalog/**` to product-service without custom Java filter code. This keeps the first route simple and consistent with the repository's declarative infrastructure style.

**Alternatives considered**:

- Java route locator bean: unnecessary for a static route and adds code without behavior value.

## Decision: Default page size 20, maximum page size 100

**Rationale**: The spec requires bounded results but does not set numeric bounds. These are technical safety limits for the public API, not business quota semantics. They prevent accidental unbounded catalog reads while keeping development fixtures easy to inspect.

**Alternatives considered**:

- No maximum: violates bounded result requirement.
- Much smaller maximum: makes local testing awkward without reducing meaningful risk in this first slice.
