# Implementation Plan: Product Catalog Administration

**Branch**: `010-catalog-administration` | **Date**: 2026-07-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-catalog-administration/spec.md`

**Status**: Implementing - approved; US1 MVP stabilized, US2/US3 pending

## Summary

Implement the first privileged Product Catalog Administration slice. `product-service` owns the
write model, lifecycle rules, PostgreSQL schema changes, idempotency replay, and audit evidence.
`api-gateway` exposes the admin route, preserves the verified bearer token and trace header, and
removes caller-supplied actor headers. The plan
uses the repository Clean/Hexagonal structure: admin web adapter -> application input ports/use
cases -> Product domain aggregate/policies -> persistence output port/adapter.

Feature 010 does not implement login/token issuance and does not publish Kafka events or create an
outbox. Real external admin E2E is blocked until a separate approved Authentication feature defines
the JWT/JWKS runtime trust contract that lets `authentication-service` issue a `CATALOG_ADMIN`
authority and lets gateway/product-service validate it.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.x.

**Primary Dependencies**:

- Existing product-service: Spring Boot Web, Spring Data JPA, JDBC, Actuator, Liquibase, PostgreSQL
  driver, Testcontainers, and MapStruct for service-local adapter DTO mapping.
- Existing api-gateway: Spring Cloud Gateway WebFlux and Actuator. `libs/common-web` contains shared
  technical HTTP envelope types, but api-gateway does not depend on it until gateway code actually
  imports those shared response types.
- Required security dependency additions before implementation: Spring Security resource-server/JWT
  support in `api-gateway` and `product-service`. The matching authentication-service token
  foundation is not implemented by Feature 010; it is recorded as a blocked prerequisite in
  `contracts/authentication-jwt-jwks-prerequisite.md`.
- Required product-service mapper dependency addition: MapStruct is allowed only for mechanical
  service-local adapter mapping between HTTP DTOs and application command/result models. Mapper
  interfaces stay beside the owning adapter and MUST NOT contain business rules or leak into
  `domain`.
- Required product-service validation dependency addition for T062: Spring Boot Starter Validation
  is used only by the admin HTTP adapter's request-body DTO and schema-bound text limits. Required
  trace/idempotency headers are normalized and bounded by a small adapter helper before mapping;
  body-field validation is not duplicated in controller code. Domain/application invariants remain
  independent of Jakarta Validation.
- No Kafka, Redis, outbox, gRPC, or shared business library dependency in Feature 010. The
  `common-web` module is a shared technical HTTP contract module only; it must not contain service
  domain models, JPA entities, or service-specific business error codes. Services should add a
  Maven dependency on it only when production code imports those shared HTTP contract types.

**Storage**: Product-service PostgreSQL remains the durable source of truth. Feature 008 tables are
reused for catalog data. Feature 010 adds service-owned Liquibase migration(s) for admin
idempotency replay and privileged mutation audit evidence.

**Testing**: JUnit 5, Spring Boot Test, MockMvc or TestRestTemplate for product-service HTTP,
Spring Cloud Gateway route/security tests, Testcontainers PostgreSQL for persistence and migration
coverage. ArchUnit may be added only if dependency direction drift appears during implementation.

**Target Platform**: Maven monorepo services, local Docker Compose backing services, future
Kubernetes Service/DNS. No root `infra/` or Kubernetes manifest change is required by this feature.

**Project Type**: Java microservice monorepo with independently buildable Spring Boot services.

**Performance Goals**:

- Administration list responses are bounded; default page size 20, maximum 100 unless the HTTP
  contract is later tightened during review.
- Admin writes are correctness-first and lower volume than shopper reads; no load-test gate is
  required for Feature 010.

**Constraints**:

- Public admin traffic enters through `api-gateway`.
- Product-service revalidates `CATALOG_ADMIN`; gateway enforcement alone is not trusted.
- Product code, Product slug, and Variant SKU become immutable after the Product first reaches
  `ACTIVE`.
- Every mutation is atomic all-or-nothing.
- Update/lifecycle commands require expected Product version and reject stale writes as conflict.
- Create/lifecycle idempotency keys replay the original outcome for 7 days.
- No Kafka/outbox/event publication in this feature.

**Scale/Scope**: One Product-owned administration slice covering Product content, Variants/base VND
prices, existing Category memberships, Product/Variant media URL metadata, lifecycle control, audit,
and idempotency.

## Constitution Check

*GATE result before Phase 0 research: PASS. Re-check after Phase 1 design: PASS.*

- **Specification traceability**: HD-001 through HD-005 are resolved in `spec.md`. Design maps to
  FR-001 through FR-013, NFR-001 through NFR-004, and SC-001 through SC-004. No observable behavior
  is added beyond the clarified spec.
- **Service ownership**: Product-service owns catalog writes, schema migrations, JPA entities,
  repositories, domain/application models, idempotency records, and audit records. No service reads
  another service database and no JPA/domain model is shared.
- **Communication**: External admin ingress uses api-gateway. The admin HTTP contract is documented
  in `contracts/product-catalog-admin-http.md`. No synchronous internal service call is added by the
  product write path. Authentication/token issuance is a prerequisite outside product-service.
- **Data and messaging**: PostgreSQL remains durable truth. Redis Lua is N/A because Feature 010 has
  no stock/reservation path. Kafka, consumers, outbox, ordering, redelivery, and event recovery are
  N/A because Feature 010 publishes no events.
- **Root infrastructure ownership**: No shared Docker, Kubernetes, Helm, or monitoring asset is
  changed. Service runtime configuration remains service-owned; product migrations remain under
  product-service resources.
- **Observability**: Existing declarative Actuator health/readiness/Prometheus exposure remains.
  Trace ID and the verified JWT identity reach product-service; caller-supplied actor headers are
  removed, and product-service derives the audited actor from its own verified security context.
  No Java code constructs `PrometheusMeterRegistry`.
- **Contracts and dependencies**: A new administration HTTP contract is provided before code. New
  security dependencies are identified here and must be added only by approved tasks. No Kafka
  contract is required.
- **Validation**: Required validation includes product-service module verification,
  api-gateway module verification, persistence/migration integration tests, security/HTTP contract
  tests, concurrency/idempotency tests, and a quickstart walkthrough. Load and Kubernetes checks are
  N/A unless implementation touches those areas.

## Phase 0 Research

See [research.md](research.md).

## Phase 1 Design

See [data-model.md](data-model.md), [contracts/product-catalog-admin-http.md](contracts/product-catalog-admin-http.md),
and [quickstart.md](quickstart.md).

## Context and Service Ownership

- **Bounded context/capability**: Product Catalog Administration inside product-service's Product
  catalog ownership.
- **Owning service**: `services/product-service`.
- **Edge service**: `services/api-gateway` only for admin route/security enforcement, preserving the
  bearer token/trace identity, and removing caller-supplied actor headers.
- **Gateway package profile**: [ADR 0003](../../docs/adr/0003-lean-api-gateway-package-structure.md)
  organizes this edge service by technical responsibility (`filter`, `security`, `error`, routing,
  and operational concerns) rather than retaining empty business-service
  `domain/application/adapter` layers. This is a package-only refinement and does not alter the
  ingress or security trust boundary.
- **Authentication prerequisite**: `services/authentication-service` owns login/token issuance and
  must provide a JWT carrying `CATALOG_ADMIN` before end-to-end admin security can be implemented.
  The missing issuer/audience/signature/JWKS/claim/rotation/expiry contract is recorded in
  [contracts/authentication-jwt-jwks-prerequisite.md](contracts/authentication-jwt-jwks-prerequisite.md);
  until that separate Authentication feature is approved, real external admin E2E remains blocked.
- **Source of truth**: Product-service PostgreSQL database `product_db`.
- **Downstream consumers**: None in Feature 010. Feature 009 shopper reads continue using
  product-service state.

## Architecture and Hexagonal Mapping

### Driving Adapters

- `adapter/in/web/admin`: admin REST controller, request/response DTOs, validation, HTTP error
  mapping, and actor/trace extraction.
- `api-gateway/filter/global`: gateway-wide request-boundary filtering for the administration path.
- `api-gateway/security`: reactive JWT authorization and stable security failure handling.
- `api-gateway/error`: gateway-owned HTTP error shape and serialization.
- Declarative route configuration remains in `api-gateway/src/main/resources/application.yml`;
  `api-gateway/routing` is reserved for Java route definitions only when an approved feature needs
  them.

### Input Ports and Use Cases

Place use-case contracts under `application/port/in` and implementations under
`application/usecase` or the existing `application/service` package if implementation chooses to
stay consistent with Feature 009.

- `CreateProductDraftUseCase`
- `BrowseAdminCatalogUseCase`
- `ViewAdminProductUseCase`
- `MaintainProductCompositionUseCase`
- `PublishProductUseCase`
- `DeactivateProductUseCase`
- `ReactivateProductUseCase`
- `ArchiveProductUseCase`

Application commands/results stay separate from HTTP DTOs and JPA entities.

### Domain Model and Policies

- Product aggregate candidate protects Product lifecycle, immutable published identifiers,
  publication prerequisites, and ownership invariants.
- Value objects/policies include Product status, Variant status, VND Money, natural keys,
  ProductMedia ownership, CategoryMembership primary rule, and lifecycle transition policy.
- Domain/application failures express duplicate key, missing target, validation, stale version,
  unsupported transition, immutable identifier, archived mutation, and idempotency-key reuse.

### Output Ports

- `LoadAdminProductPort`: load full Product aggregate/detail for admin use cases.
- `SaveAdminProductPort`: persist Product aggregate mutation with expected version.
- `CheckCatalogUniquenessPort`: detect code/slug/SKU/barcode conflicts.
- `LoadExistingCategoriesPort`: verify Category IDs exist without managing taxonomy.
- `AdminIdempotencyPort`: reserve key, replay existing outcome, store completed outcome, reject
  same key with different request hash.
- `RecordCatalogAdminAuditPort`: persist privileged mutation audit evidence.

### Driven Adapters

- `adapter/out/persistence/admin`: JPA entities/repositories/adapters for Product write model,
  Variant, Category membership, Media, idempotency, and audit records.
- Existing public query adapter remains separate and must not reuse admin command/result models as
  shopper DTOs.

### Approved Simplifications

- No Kafka/outbox.
- No binary media upload or object storage integration.
- No Category hierarchy CRUD.
- No Redis, gRPC, or cross-service synchronous call from product-service.
- No hard delete.

## Synchronous and Asynchronous Flows

### Admin Request Flow

```text
Admin UI/client
  -> api-gateway /api/v1/admin/catalog/**
  -> JWT validation and route forwarding
  -> product-service admin web adapter
  -> application input port/use case
  -> Product domain policies
  -> persistence output ports
  -> PostgreSQL transaction
  -> HTTP response
```

### Mutation Transaction Boundary

- One product-service transaction wraps the accepted domain mutation, Product persistence,
  idempotency outcome storage where applicable, and `SUCCESS` audit record. Failure of any member
  rolls the accepted command back atomically.
- `REPLAYED`, duplicate-key, and idempotency-conflict audit records are written only after the main
  mutation transaction has returned or rolled back, through a separate `REQUIRES_NEW` audit
  transaction. This preserves rejected/conflict evidence without weakening success-path atomicity.
- Authorization failures are rejected at the HTTP security boundary before a use case executes;
  callers without a verified actor cannot create a privileged mutation audit record.

### Asynchronous Flow

No asynchronous publication or consumption is introduced. Kafka/outbox integration is explicitly
deferred to a later feature.

## Data and Concurrency Design

- Add a Liquibase changeset under
  `services/product-service/src/main/resources/db/changelog/changes/002-create-product-admin-support.sql`.
- Add `product_admin_idempotency_keys` for create/lifecycle replay with 7-day `expires_at`.
- Add `product_admin_audit_logs` for actor, trace, command target, outcome, and error evidence.
- Use existing `products.version` as the Product aggregate expected version.
- Use JPA `@Version` or explicit conditional update, but the chosen implementation must produce a
  deterministic stale-version conflict instead of last-write-wins.
- Multi-part composition commands are atomic all-or-nothing.
- For create-draft, the idempotency adapter acquires a PostgreSQL transaction-scoped advisory lock
  derived from `(actor_id, idempotency_key)` before resolving the key. The lock is held by the
  existing mutation transaction through Product save and completed-outcome storage, serializing the
  same logical key across service instances without exposing PostgreSQL types to application code.
- Idempotency key replay is required for create and lifecycle commands. Update/composition commands
  use expected version; they may omit idempotency unless the HTTP contract later marks a command as
  lifecycle-equivalent.
- Expired idempotency keys are not replayable. At or after `expires_at`, the same actor/key may be
  claimed for a fresh command; persistence must atomically remove or replace the expired replay row
  so the existing unique `(actor_id, idempotency_key)` constraint cannot leak a database conflict.
  The new command runs normal validation/conflict rules, while append-only audit history remains
  retained. Cleanup may be opportunistic or scheduled inside product-service; no root infrastructure
  scheduler is required.

## Contract and Compatibility Plan

- New contract: [contracts/product-catalog-admin-http.md](contracts/product-catalog-admin-http.md).
- Base path: `/api/v1/admin/catalog`.
- Every admin endpoint requires `X-Trace-Id`; gateway preserves it, rejects an invalid boundary
  value, and does not establish actor identity from caller-supplied actor headers. Product-service
  derives the actor from its independently verified JWT security context.
- Admin browse search trims `q` and performs a case-insensitive literal substring match across
  Product code, slug, name, and Variant SKU. Results are ordered by `updated_at DESC, id ASC`.
- Gateway route must not weaken existing `/api/v1/catalog/**` shopper contract.
- Error responses use stable codes for unauthorized/forbidden, invalid request, not found,
  duplicate natural key, immutable identifier, invalid lifecycle transition, stale version,
  idempotency conflict, and archived mutation.
- The gateway owns only the Feature 010 edge failures `INVALID_ADMIN_REQUEST`, `UNAUTHENTICATED`,
  and `CATALOG_ADMIN_REQUIRED`. Product/domain failures remain owned by product-service and are
  forwarded unchanged. Rate-limit, circuit-breaker, upstream timeout/unavailable, route-not-found,
  and global internal-error codes require a later approved contract before implementation.
- Versioning strategy: first admin contract uses `/api/v1`; breaking changes require a versioned
  path or explicit compatibility section update.
- No Kafka contract is created for Feature 010.

## Failure, Retry, and Compensation

- Unauthorized/forbidden: reject before use-case mutation; no Product state change.
- Duplicate code/slug/SKU/barcode: reject; no partial state change.
- Missing Category/media Variant ownership mismatch: reject; no partial state change.
- Stale expected version: return conflict; no partial state change.
- Idempotency replay: same actor/key/request hash within 7 days returns original stored outcome.
- Same actor/key with different request hash returns idempotency conflict.
- Expired idempotency key: do not replay the old outcome; atomically treat the key as a fresh claim
  and execute the new command under normal validation/conflict rules without deleting prior audit
  history.
- Partial failure in persistence transaction rolls back all catalog mutation.
- No compensation workflow is needed because Feature 010 has no remote side effect or event
  publication.

## Security Boundaries

- Gateway validates JWT and routes `/api/v1/admin/catalog/**` only for `CATALOG_ADMIN`.
- Product-service revalidates `CATALOG_ADMIN` and rejects missing/invalid actor identity.
- Product-service derives actor ID from its verified JWT security context. Caller-supplied actor
  headers are never an identity source.
- Runtime JWT issuer, audience, signature algorithm, JWKS endpoint, `kid` rotation, token lifetime,
  refresh/revocation, and local real-token issuance are intentionally unresolved here and must be
  owned by a separate Authentication feature before real external admin E2E is claimed.
- HTTP request bodies must not be reused as application commands without validation/mapping.
- Admin responses may include non-public Product states but must not include secrets, password data,
  payment data, stock reservation data, or data owned by another service.

## Observability

- Keep existing liveness/readiness/Prometheus endpoint configuration.
- Ensure `X-Trace-Id` or equivalent trace identity reaches product-service admin adapter.
- Every privileged mutation audit record includes actor ID, trace ID, command name, target Product
  ID when available, result, and error code when rejected.
- Structured logs should include trace ID, actor ID, command name, target, result, and Product
  version, without logging full request bodies or JWTs.

## Migration and Rollback

- Forward migration adds admin support tables only; existing Product catalog tables remain
  compatible with Feature 009 reads.
- No table rename/drop and no destructive data migration.
- Rollback drops admin audit/idempotency tables before the feature is released with real admin data.
  After real admin data exists, rollback is mitigation-oriented: disable admin route and keep tables
  until a reviewed data-retention decision.
- Migration verification uses product-service migration integration tests with Testcontainers
  PostgreSQL.

## Verification Strategy

| Requirement / Behavior | Unit | Integration | Contract | E2E | Load / Concurrency |
|------------------------|------|-------------|----------|-----|--------------------|
| Admin authorization and actor propagation | Security policy tests where practical | Product-service/gateway security tests | Admin HTTP contract | Optional local gateway flow | N/A |
| Create draft hidden from shopper catalog | Domain/use-case test | PostgreSQL + HTTP test | Admin + existing shopper contract | Optional | N/A |
| Admin browse/detail includes non-public state | Use-case test | HTTP integration test | Admin contract | Optional | N/A |
| Product/Variant/Category/media composition | Domain policy tests | Persistence integration test | Admin contract | Optional | N/A |
| Lifecycle transitions and immutable published identifiers | Domain policy tests | HTTP/persistence integration test | Admin contract | Optional | N/A |
| Optimistic locking conflict | Use-case test | Concurrent update integration test | Error contract | Optional | Required concurrency test |
| Idempotency replay for 7 days plus fresh reuse after expiry | Use-case test with controlled clock | Persistence integration and concurrent reclaim test | Header/error contract | Optional | Retry/expiry test |
| Audit evidence | Use-case test | Persistence integration test | N/A | Optional | N/A |
| No Kafka/outbox behavior | N/A | Assert no outbox/event artifact is required | No Kafka contract | N/A | N/A |

Required commands:

```powershell
.\mvnw.cmd -pl services/product-service -am verify
.\mvnw.cmd -pl services/api-gateway -am verify
```

Run `.\mvnw.cmd clean verify` if implementation touches shared modules, root Maven configuration,
or cross-service security foundations.

## Project Structure

### Documentation

```text
specs/010-catalog-administration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── product-catalog-admin-http.md
├── checklists/
│   └── requirements.md
└── tasks.md                  # Created by speckit-tasks, not by this plan
```

### Source Code

```text
services/product-service/
├── pom.xml
├── src/main/java/com/philia/flashsale/product/
│   ├── adapter/
│   │   ├── in/web/admin/           # admin controllers, DTOs, mappers, error translation
│   │   └── out/persistence/admin/  # write-model JPA adapters/entities/repositories
│   ├── application/
│   │   ├── command/                # admin mutation commands
│   │   ├── query/                  # admin browse/detail queries
│   │   ├── result/                 # use-case results, including versions
│   │   ├── port/
│   │   │   ├── in/                 # admin use-case interfaces
│   │   │   └── out/                # persistence/idempotency/audit ports
│   │   └── usecase/                # admin orchestration
│   ├── domain/
│   │   ├── model/                  # Product aggregate/value models
│   │   ├── policy/                 # lifecycle/publication/immutability policies
│   │   └── exception/              # invariant failures with domain meaning
│   └── config/                     # Spring wiring, existing package convention
├── src/main/resources/db/changelog/changes/
│   └── 002-create-product-admin-support.sql
└── src/test/java/com/philia/flashsale/product/
    ├── adapter/in/web/admin/
    ├── adapter/out/persistence/admin/
    ├── application/usecase/
    ├── domain/
    └── integration/

services/api-gateway/
├── pom.xml
├── src/main/java/com/philia/flashsale/gateway/
│   ├── ApiGatewayApplication.java
│   ├── configuration/              # non-security WebFlux/runtime wiring when needed
│   ├── routing/                    # Java routes when declarative YAML is insufficient
│   ├── filter/
│   │   ├── global/
│   │   │   └── AdminCatalogRequestBoundaryFilter.java
│   │   └── route/
│   ├── security/
│   │   ├── GatewaySecurityConfiguration.java
│   │   └── GatewaySecurityErrorHandler.java
│   ├── error/
│   │   ├── GatewayErrorCode.java
│   │   ├── GatewayErrorResponse.java
│   │   └── GatewayHttpErrorWriter.java
│   ├── faulttolerance/
│   ├── ratelimit/
│   └── observability/
├── src/main/resources/application.yml
└── src/test/java/com/philia/flashsale/gateway/

libs/common-web/
├── pom.xml
└── src/main/java/com/philia/flashsale/common/web/
    ├── ApiResponse.java
    ├── ApiErrorResponse.java
    ├── FieldViolation.java
    ├── PageMeta.java
    └── PageResponse.java
```

**Structure Decision**: Use service-local Clean/Hexagonal boundaries. Existing Feature 009 public
reader packages remain valid and separate. Admin request/response DTOs, application commands,
domain models, and JPA entities must not be reused across boundaries. `product-service` follows
that business-service profile. `api-gateway` follows the lean edge profile accepted in ADR 0003;
its empty legacy `domain/application/adapter` scaffold is removed because the gateway owns no
business core, use case, or durable adapter in this feature.
Shared HTTP envelope placeholders live in `libs/common-web` as technical contract types only; each
service keeps its own domain/application models and service-specific error codes.

## Complexity Tracking

No constitutional violation or ADR exception is required.

## Plan Change History

| Date | Change | Approval |
|------|--------|----------|
| 2026-07-20 | Approved US1 stabilization design for transaction-scoped idempotency serialization, durable outcome audit, adapter validation dependency, required trace boundary, and deterministic admin search/order | Product owner approval of T057 and T059-T065 stabilization group |
| 2026-07-20 | Implemented and verified T059-T065; retained T066-T067 and unchecked US2/US3 as subsequent work | Approved stabilization scope completed locally |
| 2026-07-20 | Approved ADR 0003 and a behavior-neutral gateway package cleanup: move the five existing edge classes into `filter/global`, `security`, and `error`; remove the empty business-service scaffold; retain the approved technical edge markers; require a clean gateway verify | Product owner approval of T068 package cleanup |
| 2026-07-21 | Completed T068 and verified the lean gateway package profile with a clean module build; all eight gateway tests passed and no old-package bytecode remained | Approved T068 scope completed locally |
| 2026-07-21 | Completed T066 by recording the Authentication JWT/JWKS prerequisite as blocked pending a separate approved Authentication feature; Feature 010 keeps test-only JWT verification and must not claim real external admin E2E yet | Approved T066 scope completed locally |
| 2026-07-21 | Promoted `ApiResponse`, `ApiErrorResponse`, `FieldViolation`, `PageMeta`, and `PageResponse` to `libs/common-web` as a technical shared HTTP envelope baseline; gateway keeps no unused dependency until it actually imports the shared types, and business/domain error ownership remains service-local | Product owner approval in chat |
| 2026-07-22 | Approved behavior-neutral gateway error cleanup: rename the admin-named edge body to `GatewayErrorResponse`, type only the three Feature 010 gateway-owned codes, remove unrequested empty Java placeholders, retain marker-only future technical packages, and preserve the existing HTTP contract | Product owner approval in chat; T070 |
