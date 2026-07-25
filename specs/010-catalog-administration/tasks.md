# Tasks: Product Catalog Administration

**Input**: Design artifacts from `specs/010-catalog-administration/`

**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/product-catalog-admin-http.md](contracts/product-catalog-admin-http.md), [quickstart.md](quickstart.md)

**Status**: Approved by product owner on 2026-07-19 - implementation started; US1 convergence tasks
T057-T067 completed through 2026-07-24; T068-T069 gateway/shared contract cleanup completed on
2026-07-21; US2/US3 remain pending

**Tests**: Tests are required by the plan because this feature changes privileged writes, lifecycle visibility, Money validation, optimistic locking, idempotency replay, and audit evidence.

## Phase 1: Setup

**Purpose**: Prepare dependencies, migration hook, and security configuration surfaces.

- [X] T001 Add Spring Security OAuth2 resource-server dependencies to services/product-service/pom.xml and services/api-gateway/pom.xml
- [X] T002 Add admin route skeleton configuration for /api/v1/admin/catalog/** in services/api-gateway/src/main/resources/application.yml
- [X] T003 Add product admin package documentation marker or README only if needed under services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/
- [X] T004 Update services/product-service/src/main/resources/db/changelog/db.changelog-master.yaml to include the Feature 010 admin support changeset

---

## Phase 2: Foundation

**Purpose**: Create cross-story primitives that all admin use cases rely on. This phase blocks all user stories.

- [X] T005 Add Liquibase migration for product_admin_idempotency_keys and product_admin_audit_logs in services/product-service/src/main/resources/db/changelog/changes/002-create-product-admin-support.sql
- [X] T006 Add migration integration test coverage for admin support tables in services/product-service/src/test/java/com/philia/flashsale/product/ProductMigrationIT.java
- [X] T007 Add admin domain enums/value objects for ProductStatus, VariantStatus, MoneyVnd, CatalogAdminActor, TraceId, and AdminCommandName in services/product-service/src/main/java/com/philia/flashsale/product/domain/model/
- [X] T008 Add admin domain exceptions for invalid lifecycle, immutable identifier, archived mutation, invalid money, ownership mismatch, and publication prerequisite failures in services/product-service/src/main/java/com/philia/flashsale/product/domain/exception/
- [X] T009 Add Product aggregate and lifecycle/publication policies in services/product-service/src/main/java/com/philia/flashsale/product/domain/model/ProductAggregate.java and services/product-service/src/main/java/com/philia/flashsale/product/domain/policy/ProductLifecyclePolicy.java
- [X] T010 Add application command/query/result records shared by admin use cases in services/product-service/src/main/java/com/philia/flashsale/product/application/command/, services/product-service/src/main/java/com/philia/flashsale/product/application/query/, and services/product-service/src/main/java/com/philia/flashsale/product/application/result/
- [X] T011 Add admin output ports LoadAdminProductPort, SaveAdminProductPort, CheckCatalogUniquenessPort, LoadExistingCategoriesPort, AdminIdempotencyPort, and RecordCatalogAdminAuditPort in services/product-service/src/main/java/com/philia/flashsale/product/application/port/out/
- [X] T012 Add product-service admin security configuration enforcing CATALOG_ADMIN in services/product-service/src/main/java/com/philia/flashsale/product/config/ProductAdminSecurityConfiguration.java
- [X] T013 Add api-gateway security configuration enforcing CATALOG_ADMIN for /api/v1/admin/catalog/** in services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewaySecurityConfiguration.java
- [X] T014 Add admin error model and exception mapper in services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/AdminCatalogErrorResponse.java and services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminExceptionHandler.java
- [X] T015 Add admin persistence JPA entities/repositories for Product, Variant, Category membership, Media, Idempotency, and Audit in services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/
- [X] T016 Add Spring wiring for admin use cases and adapters in services/product-service/src/main/java/com/philia/flashsale/product/config/ProductAdminConfiguration.java

**Checkpoint**: Migration test should be runnable after T005-T006; compile may require later story code before full module verify.

---

## Phase 3: User Story 1 - Create and Inspect a Catalog Draft (P1)

**Goal**: An authorized catalog operator can create a non-public Product draft and inspect it through an admin view while the shopper catalog remains unchanged.

**Independent Test**: Create one valid Product draft, retrieve it through the admin view, and verify it is absent from `/api/v1/catalog/products/{slug}`.

### Tests for US1

- [X] T017 [P] [US1] Add domain tests for draft creation, duplicate code/slug rejection, and draft shopper-hidden invariant in services/product-service/src/test/java/com/philia/flashsale/product/domain/ProductAggregateDraftTests.java
- [X] T018 [P] [US1] Add application tests for CreateProductDraftUseCase idempotency replay and duplicate natural-key outcomes in services/product-service/src/test/java/com/philia/flashsale/product/application/usecase/CreateProductDraftUseCaseTests.java
- [X] T019 [P] [US1] Add admin HTTP contract tests for POST /api/v1/admin/catalog/products, GET admin list/detail, and unauthorized/forbidden outcomes in services/product-service/src/test/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminDraftHttpTests.java
- [X] T020 [P] [US1] Add persistence integration tests for draft insert, admin list/detail loading, audit write, and idempotency replay storage in services/product-service/src/test/java/com/philia/flashsale/product/adapter/out/persistence/admin/ProductAdminDraftPersistenceIT.java
- [X] T021 [P] [US1] Add api-gateway route/security tests for /api/v1/admin/catalog/** in services/api-gateway/src/test/java/com/philia/flashsale/gateway/ProductAdminGatewayRouteTests.java

### Implementation for US1

- [X] T022 [US1] Implement CreateProductDraftUseCase and Browse/View admin input ports in services/product-service/src/main/java/com/philia/flashsale/product/application/port/in/
- [X] T023 [US1] Implement draft create, admin browse, and admin detail use cases in services/product-service/src/main/java/com/philia/flashsale/product/application/usecase/
- [X] T024 [US1] Implement admin persistence adapter methods for Product draft create and admin read models in services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/
- [X] T025 [US1] Implement idempotency and audit persistence adapters for create draft in services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/
- [X] T026 [US1] Implement admin draft/list/detail request and response DTOs in services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/
- [X] T027 [US1] Implement ProductAdminController endpoints for POST /products, GET /products, and GET /products/{productId} in services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminController.java
- [X] T028 [US1] Implement api-gateway admin route/security forwarding for /api/v1/admin/catalog/** in services/api-gateway/src/main/resources/application.yml and services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewaySecurityConfiguration.java

**Checkpoint**: US1 must pass product-service admin draft tests, gateway route/security tests, and existing Feature 009 shopper catalog tests.

---

## Phase 4: User Story 2 - Maintain Product Composition (P2)

**Goal**: An authorized catalog operator can maintain Product content, Variants/base VND prices, existing Category memberships, and media URL metadata atomically.

**Independent Test**: Starting from one draft, apply composition changes and verify admin detail plus all affected integrity rules without publishing.

### Tests for US2

- [ ] T029 [P] [US2] Add domain tests for Variant Money, ownership mismatch, one primary Category, media Variant ownership, and all-or-nothing composition in services/product-service/src/test/java/com/philia/flashsale/product/domain/ProductCompositionPolicyTests.java
- [ ] T030 [P] [US2] Add application tests for MaintainProductCompositionUseCase validation, expected version, immutable published identifiers, and archived mutation rejection in services/product-service/src/test/java/com/philia/flashsale/product/application/usecase/MaintainProductCompositionUseCaseTests.java
- [ ] T031 [P] [US2] Add HTTP contract tests for PUT /api/v1/admin/catalog/products/{productId}/composition success and stable error codes in services/product-service/src/test/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminCompositionHttpTests.java
- [ ] T032 [P] [US2] Add persistence integration tests for Variant, Category membership, Media replacement, duplicate SKU/barcode, and missing Category rollback in services/product-service/src/test/java/com/philia/flashsale/product/adapter/out/persistence/admin/ProductAdminCompositionPersistenceIT.java

### Implementation for US2

- [ ] T033 [US2] Implement MaintainProductCompositionUseCase input port in services/product-service/src/main/java/com/philia/flashsale/product/application/port/in/MaintainProductCompositionUseCase.java
- [ ] T034 [US2] Implement composition domain methods and policies in services/product-service/src/main/java/com/philia/flashsale/product/domain/model/ProductAggregate.java and services/product-service/src/main/java/com/philia/flashsale/product/domain/policy/
- [ ] T035 [US2] Implement MaintainProductCompositionUseCase orchestration in services/product-service/src/main/java/com/philia/flashsale/product/application/usecase/MaintainProductCompositionService.java
- [ ] T036 [US2] Implement persistence save/load for Product composition, uniqueness checks, and Category existence checks in services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/
- [ ] T037 [US2] Implement composition request/response DTOs and mapper in services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/
- [ ] T038 [US2] Add PUT /products/{productId}/composition endpoint to services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminController.java
- [ ] T039 [US2] Extend admin exception handling with duplicate SKU/barcode, immutable identifier, missing Category, ownership mismatch, and archived mutation mappings in services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminExceptionHandler.java

**Checkpoint**: US2 must pass composition domain/application/HTTP/persistence tests and preserve US1 behavior.

---

## Phase 5: User Story 3 - Control Shopper Visibility (P3)

**Goal**: An authorized catalog operator can publish, deactivate, reactivate, and archive Products through explicit lifecycle actions.

**Independent Test**: Move a prepared Product through DRAFT -> ACTIVE, ACTIVE -> INACTIVE, INACTIVE -> ACTIVE, and final ARCHIVED, then verify admin state and Feature 009 shopper visibility after each transition.

### Tests for US3

- [ ] T040 [P] [US3] Add domain lifecycle tests for allowed transitions, publication prerequisites, archived finality, and no hard delete in services/product-service/src/test/java/com/philia/flashsale/product/domain/ProductLifecyclePolicyTests.java
- [ ] T041 [P] [US3] Add application tests for publish, deactivate, reactivate, archive, expected version conflict, idempotency replay, and audit outcomes in services/product-service/src/test/java/com/philia/flashsale/product/application/usecase/ProductLifecycleUseCaseTests.java
- [ ] T042 [P] [US3] Add HTTP contract tests for /publish, /deactivate, and /archive success/replay/conflict/error responses in services/product-service/src/test/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminLifecycleHttpTests.java
- [ ] T043 [P] [US3] Add persistence/concurrency integration tests for stale Product version conflict and lifecycle visibility compatibility with shopper catalog queries in services/product-service/src/test/java/com/philia/flashsale/product/integration/ProductAdminLifecycleIT.java

### Implementation for US3

- [ ] T044 [US3] Implement PublishProductUseCase, DeactivateProductUseCase, ReactivateProductUseCase, and ArchiveProductUseCase input ports in services/product-service/src/main/java/com/philia/flashsale/product/application/port/in/
- [ ] T045 [US3] Implement lifecycle application services in services/product-service/src/main/java/com/philia/flashsale/product/application/usecase/
- [ ] T046 [US3] Implement lifecycle persistence updates with expected Product version and idempotency replay in services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/
- [ ] T047 [US3] Add lifecycle DTOs and controller endpoints to services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminController.java
- [ ] T048 [US3] Extend audit recording for lifecycle success, replay, rejected, and conflict outcomes in services/product-service/src/main/java/com/philia/flashsale/product/application/usecase/ and services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/
- [ ] T049 [US3] Verify Feature 009 public catalog query behavior remains unchanged after lifecycle transitions in services/product-service/src/test/java/com/philia/flashsale/product/ProductCatalogQueryTests.java

**Checkpoint**: US3 must pass lifecycle, concurrency, idempotency, audit, and shopper compatibility tests.

---

## Phase 6: Polish and Cross-Cutting Verification

**Purpose**: Finish documentation alignment, validation evidence, and architecture hygiene.

- [ ] T050 Update specs/010-catalog-administration/quickstart.md only if implementation commands or local profiles differ from the plan
- [ ] T051 Verify no Kafka contract, outbox table, Redis adapter, binary media upload, Category hierarchy CRUD, or cross-service database access was introduced for Feature 010
- [ ] T052 Run product-service module verification with .\mvnw.cmd -pl services/product-service -am verify and record result in task completion notes
- [ ] T053 Run api-gateway module verification with .\mvnw.cmd -pl services/api-gateway -am verify and record result in task completion notes
- [ ] T054 Run .\mvnw.cmd clean verify if shared Maven/root security foundations were changed and record result in task completion notes
- [ ] T055 Review changed files against docs/architecture/service-clean-hex-structure.md and ensure no HTTP DTO, JPA entity, or framework exception leaks into application/domain
- [ ] T056 Update specs/010-catalog-administration/checklists/requirements.md CHK017-CHK020 after contract, migration, traceability, and verification evidence are complete

---

## Dependencies

- Phase 1 Setup must complete before Phase 2 Foundation.
- Phase 2 Foundation must complete before any user story.
- US1 is the MVP and should be implemented first.
- US2 depends on US1 because composition requires an existing Product draft and admin detail.
- US3 depends on US2 because publication requires a composed Product with at least one active Variant and valid VND base price.
- Polish depends on US1-US3 completion.

## Parallel Opportunities

- T017-T021 can run in parallel after Foundation because they touch separate test files.
- T029-T032 can run in parallel after US1 because they touch separate test files.
- T040-T043 can run in parallel after US2 because they touch separate test files.
- Within implementation phases, tasks touching the same controller, persistence adapter, or aggregate must run sequentially.

## Implementation Strategy

### MVP First

Complete Phase 1, Phase 2, and Phase 3 (US1). This produces a useful admin draft creation and inspection slice without publishing or full composition.

### Incremental Delivery

1. Build US1 and verify draft creation stays hidden from shopper catalog.
2. Add US2 composition and verify all-or-nothing Product-owned mutations.
3. Add US3 lifecycle and verify shopper visibility compatibility.
4. Run full feature validation and update checklist evidence.

### Boundary Rules During Implementation

- Web request/response DTOs stay under `adapter/in/web/admin`.
- Application commands/results/use cases stay under `application`.
- Domain invariants and lifecycle policies stay under `domain`.
- JPA entities/repositories and SQL-specific behavior stay under `adapter/out/persistence/admin`.
- Configuration wires beans but does not decide business behavior.
- No service module may depend on another service module.

## Completion Notes

### 2026-07-19 - MVP slice completed through US1

- Completed Phase 1, Phase 2, and Phase 3 (US1) only.
- T003 did not require a package README/marker because the admin web adapter package now contains concrete controller, DTO, and exception-handler classes with clear package placement.
- Implemented admin draft create, admin browse, and admin detail without adding Kafka contracts, outbox publication, Redis adapters, binary media upload, Category CRUD, or cross-service database access.
- Validation evidence:
  - `.\mvnw.cmd -pl services/product-service -am verify` -> PASS, 11 tests, finished 2026-07-19T16:46:24+07:00.
  - `.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify` -> PASS, 15 tests, finished 2026-07-19T20:52:02+07:00.
  - `.\mvnw.cmd -pl services/api-gateway -am verify` -> PASS, 5 tests, finished 2026-07-19T20:59:00+07:00.
- 2026-07-20 package hygiene follow-up: moved gateway Spring security wiring from `gateway/config` to the canonical `gateway/configuration` package and removed the empty parallel `config` directory. Validation: `.\mvnw.cmd -pl services/api-gateway -am verify` -> PASS, 5 tests, finished 2026-07-20T13:31:03+07:00.
- 2026-07-20 product-service mapper follow-up: introduced MapStruct for service-local HTTP adapter
  DTO mapping, keeping mapper interfaces beside public/admin web adapters and out of
  application/domain. Validation: `.\mvnw.cmd -pl services/product-service -am verify` -> PASS,
  11 tests, finished 2026-07-20T17:07:14+07:00.
- Remaining unchecked work starts at US2 composition (T029+) and US3 lifecycle (T040+). Polish tasks remain unchecked until the full Feature 010 scope is complete.

---

## Phase 7: Convergence

**Purpose**: Stabilize the implemented US1 MVP before starting another feature. These tasks do not
replace the already-unchecked US2/US3 work. T057-T067 MUST be reviewed and approved as a coherent
stabilization group before production-code implementation resumes.

- [X] T057 CRITICAL: Reconcile approval status and history across `specs/010-catalog-administration/spec.md`, `specs/010-catalog-administration/plan.md`, `specs/010-catalog-administration/tasks.md`, and `specs/010-catalog-administration/checklists/requirements.md` before further production work per Constitution I and the spec-driven approval gate (contradicts)
- [X] T058 Resolve and record whether an expired admin idempotency key may start a fresh command after the 7-day replay window, updating the canonical requirement, HTTP contract, plan, and affected task scope before implementation per FR-011 and HD-004 (partial)
- [X] T059 Implement an atomic create-draft idempotency reservation/replay boundary for concurrent same-key requests in `services/product-service/src/main/java/com/philia/flashsale/product/application/port/out/AdminIdempotencyPort.java` and `services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/`, with clock-controlled expiry and concurrency coverage in `services/product-service/src/test/java/com/philia/flashsale/product/application/usecase/CreateProductDraftUseCaseTests.java` and `services/product-service/src/test/java/com/philia/flashsale/product/adapter/out/persistence/admin/ProductAdminDraftPersistenceIT.java`, per FR-011, NFR-002, and the plan's reserve-key decision (partial)
- [X] T060 Make create-draft success, replay, duplicate, and idempotency-conflict audit outcomes durable without rolling rejected outcomes back with the failed catalog transaction, updating `services/product-service/src/main/java/com/philia/flashsale/product/application/usecase/CreateProductDraftService.java`, `services/product-service/src/main/java/com/philia/flashsale/product/config/ProductAdminConfiguration.java`, the admin audit persistence boundary, and integration tests per NFR-003 and the plan's audit evidence decision (contradicts)
- [X] T061 Translate concurrent Product code/slug database uniqueness races into stable `DUPLICATE_PRODUCT_CODE` or `DUPLICATE_PRODUCT_SLUG` application outcomes in `services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/admin/`, with concurrent persistence/HTTP tests per US1/AC2 and FR-009 (partial)
- [X] T062 Add adapter-owned create-draft request validation and stable `INVALID_ADMIN_REQUEST` translation for missing headers, malformed JSON, invalid identifiers/enums, blank fields, and schema-bounded oversized fields in `services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/admin/`; update and approve `specs/010-catalog-administration/plan.md` before adding any validation dependency to `services/product-service/pom.xml`, and add HTTP contract tests per FR-009 and the admin HTTP error contract (partial)
- [X] T063 Return the documented stable `UNAUTHENTICATED` and `CATALOG_ADMIN_REQUIRED` error bodies from both `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewaySecurityConfiguration.java` and `services/product-service/src/main/java/com/philia/flashsale/product/config/ProductAdminSecurityConfiguration.java`, with body assertions in gateway and product-service security tests per FR-001, NFR-001, and the admin HTTP error contract (partial)
- [X] T064 Enforce and preserve the documented actor and `X-Trace-Id` boundary for every US1 administration request through gateway and product-service, adding read/write header and failure-path assertions without trusting caller-supplied actor headers per NFR-003 and Constitution VIII (partial)
- [X] T065 Complete US1 admin-read coverage for status/search filters, bounded paging, deterministic ordering, empty results, and missing detail across `services/product-service/src/test/java/com/philia/flashsale/product/adapter/in/web/admin/ProductAdminDraftHttpTests.java` and `services/product-service/src/test/java/com/philia/flashsale/product/adapter/out/persistence/admin/ProductAdminDraftPersistenceIT.java` per US1/AC3-AC4 and NFR-004 (partial)
- [X] T066 Link an approved `authentication-service` JWT/JWKS prerequisite that defines the canonical `CATALOG_ADMIN` claim and runtime trust contract, or explicitly record external admin E2E as blocked; do not implement token issuance inside product-service or api-gateway per FR-001 and the plan's authentication dependency (blocked prerequisite recorded)
- [X] T067 Add a positive api-gateway-to-Product US1 forwarding test that verifies method, path, query, request body, response, `Authorization`, `X-Trace-Id`, and `Idempotency-Key` preservation using an isolated test upstream, while keeping real JWT/JWKS verification governed by T066, per FR-001 and plan: Admin Request Flow (partial)

### 2026-07-20 - T058 idempotency expiry clarification completed

- Product owner approved fresh reuse of the same actor/idempotency key after the 7-day replay
  window. The expired outcome is never replayed; normal validation/conflict behavior applies to the
  fresh command; prior audit history remains retained.
- Updated `spec.md`, `plan.md`, `data-model.md`, and the admin HTTP contract. Documentation-only
  clarification; no production code or test behavior changed in this task.

### 2026-07-20 - T057 approval/status reconciliation completed

- Synchronized `spec.md`, `plan.md`, and this task ledger to the product owner's recorded approvals
  and the actual state: US1 is implemented, T057/T059-T065 are the approved stabilization group,
  and unchecked US2/US3 work remains incomplete.
- Re-evaluated CHK017-CHK020 against the existing approved HTTP contract, migration/rollback plan,
  requirement-to-test matrix, and risk-based verification commands. All four design-quality checks
  pass; they do not claim that the remaining implementation tests have already passed.
- Validation: prerequisite script resolved `specs/010-catalog-administration`; checklist scan reports
  21 total, 21 complete, 0 incomplete after reconciliation.

### 2026-07-20 - T059-T065 Product MVP stabilization completed

- T059 serializes the same `(actor_id, idempotency_key)` with a PostgreSQL transaction-scoped
  advisory lock. Same-request retries replay one stored result, different payloads conflict, and a
  key is reclaimed as a fresh command at the exact 7-day boundary under a controlled clock.
- T060 keeps Product creation, replay storage, and the `SUCCESS` audit atomic. `REPLAYED` and
  rejected `CONFLICT` evidence is appended in a separate `REQUIRES_NEW` transaction, so a failed
  mutation cannot erase its audit record.
- T061 flushes Product inserts at the persistence boundary and translates the named PostgreSQL
  code/slug unique constraints into stable application errors. Concurrent persistence and HTTP
  tests exercise the real database race for both natural keys.
- T062-T064 add adapter-owned Jakarta validation, stable 400/401/403 JSON errors, required bounded
  trace IDs on every US1 endpoint, caller actor-header removal at the gateway, and actor derivation
  from product-service's independently verified JWT context.
- T065 fixes literal, case-insensitive Product code/slug/name/Variant-SKU search (including `%` and
  `_`), deterministic `updated_at DESC, id ASC` ordering, bounded page metadata, empty results, and
  missing detail behavior with both HTTP and persistence coverage.
- Validation: `.\mvnw.cmd -pl services/product-service -am test` -> PASS, 23 tests, finished
  2026-07-20T21:44:23+07:00.
- Validation: `.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify` -> PASS,
  23 unit/HTTP tests plus 25 persistence/migration integration tests, finished
  2026-07-20T22:05:33+07:00.
- Validation: `.\mvnw.cmd -pl services/api-gateway -am verify` -> PASS, 8 tests, finished
  2026-07-20T22:07:27+07:00.
- CI/PR reference: N/A for this local implementation run; no remote PR operation was in scope.

---

## Phase 8: API Gateway Package Cleanup

**Purpose**: Apply the lean edge package profile accepted in ADR 0003 without changing the Feature
010 route, security, trace-header, forwarding, or error contract. This task is independent of the
pending T067 forwarding work and does not start US2 or US3.

- [X] T068 Move the five existing gateway classes from
  `services/api-gateway/src/main/java/com/philia/flashsale/gateway/adapter/in/web/` and
  `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewaySecurityConfiguration.java`
  into `filter/global`, `error`, and `security`; update package declarations and
  `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ProductAdminGatewayRouteTests.java`;
  remove redundant target markers plus the empty gateway `domain/application/adapter` scaffold;
  update `docs/architecture/service-clean-hex-structure.md`; verify no old package reference or
  duplicate source remains; run
  `.\mvnw.cmd -pl services/api-gateway -am clean verify` and record the eight-test result (ADR 0003,
  FR-001, NFR-001, NFR-003)
- [X] T069 Move gateway-local HTTP envelope types `ApiResponse`, `ApiErrorResponse`,
  `FieldViolation`, `PageMeta`, and `PageResponse` into `libs/common-web` as technical shared
  contract types, add `common-web` to the root Maven reactor, keep api-gateway free of an unused
  shared dependency until it imports the shared types, and verify the gateway build without moving
  gateway/service-specific error codes or domain models into shared code.

**Approval**: Product owner explicitly approved the complete T068 cleanup scope on 2026-07-20.

### 2026-07-21 - T068 API Gateway package cleanup completed

- Moved the existing global administration boundary filter to `filter/global`, the gateway HTTP
  error model/writer to `error`, and reactive security configuration/error handling to `security`.
  The route, authority conversion, stable error bodies, trace validation, and forwarding behavior
  were not changed.
- Removed redundant `.gitkeep` files from directories that now contain real classes. Retained only
  the approved marker-only technical directories: `configuration`, `routing`, `filter/route`,
  `faulttolerance`, `ratelimit`, and `observability`.
- Removed the empty legacy gateway `domain`, `application`, and `adapter` directory trees. A source
  and test search found no import or package declaration under the old gateway packages; completed
  historical tasks keep their original implementation paths intentionally.
- Clean-build inspection found compiled gateway classes only under the root, `filter/global`,
  `error`, and `security` packages, so no stale Spring component from the old package layout remains.
- Validation: `.\mvnw.cmd -pl services/api-gateway -am clean verify` -> PASS, 8 tests, 0 failures,
  0 errors, 0 skipped; finished 2026-07-21T10:35:13+07:00.
- CI/PR reference: N/A for this local implementation run; no remote PR operation was in scope.

### 2026-07-21 - T069 shared HTTP envelope placeholder move completed

- Moved `ApiResponse`, `ApiErrorResponse`, and `FieldViolation` from the gateway-local
  `contract/http` package into `libs/common-web/src/main/java/com/philia/flashsale/common/web/`,
  then added `PageMeta` and `PageResponse` there as the pagination envelope.
- Split pagination into `PageResponse<T>(content, meta)` plus `PageMeta` so item payload and
  pagination metadata remain separate. `common-web` intentionally does not import Spring Data
  `Page`; Spring-specific mapping belongs beside the service adapter that uses Spring Data.
- Implemented the shared HTTP envelopes as Java 21 records without Lombok or Jackson annotations, so
  `common-web` stays dependency-light and does not force optional framework choices onto services.
- Added `libs/common-web` as a Maven reactor module. Kept api-gateway free of a direct
  `common-web` dependency for now because no gateway production class imports the placeholder
  envelope yet; the dependency should be added when future gateway error/response standardization
  actually uses the shared technical envelope.
- Kept gateway-owned and service-owned error codes outside the shared module. `GatewayErrorCode`,
  `GatewayException`, `GatewayHttpErrorWriter`, and security/error behavior remain gateway-owned.
- No runtime behavior was intentionally changed; no service imports the shared envelopes yet.
- Validation: `.\mvnw.cmd -pl services/api-gateway -am verify` -> PASS, reactor compiled
  `flash-sale-engine` and `api-gateway` without a direct `common-web` dependency; gateway test
  result was 8 tests, 0 failures, 0 errors, 0 skipped; finished 2026-07-21T22:36:12+07:00.
- Validation: `.\mvnw.cmd -pl libs/common-web -am verify` -> PASS, reactor compiled
  `flash-sale-engine` and `common-web`; `common-web` compiled 5 source files and has no tests;
  finished 2026-07-21T23:48:03+07:00.
- Validation: `.\mvnw.cmd clean verify` -> PASS across the 12-module reactor
  (`flash-sale-engine`, `common-web`, `api-gateway`, `authentication-service`, `product-service`,
  `cart-service`, `campaign-service`, `flashsale-service`, `order-service`, `payment-service`,
  `notification-service`, `chatting-service`); finished 2026-07-21T22:09:58+07:00.
- CI/PR reference: N/A for this local implementation run; no remote PR operation was in scope.

### 2026-07-21 - T066 Authentication prerequisite status recorded

- Added `contracts/authentication-jwt-jwks-prerequisite.md` to make the authentication boundary
  explicit for Feature 010 without implementing token issuance in `product-service` or
  `api-gateway`.
- Recorded the only Feature 010-approved authority dependency as the canonical `CATALOG_ADMIN`
  authority and documented that actor identity must come from an independently verified JWT
  security context.
- Recorded real external admin E2E as blocked until a separate approved Authentication feature
  defines and implements issuer, audience, signature algorithm, JWKS endpoint, `kid` rotation,
  required claims, expiry, refresh/revocation, and local real-token strategy.
- Updated the admin HTTP contract, plan, and quickstart to point at the blocked prerequisite. Existing
  gateway/product-service tests may continue using approved test-only JWTs; T067 remains responsible
  for isolated gateway forwarding, not real JWT/JWKS verification.
- Validation: documentation-only task; prerequisite script resolved
  `specs/010-catalog-administration` and the requirement checklist remains PASS with 21 total,
  21 complete, and 0 incomplete. Maven verification was not required because no production or test
  code changed.

### 2026-07-24 - T067 Gateway-to-Product forwarding completed

- Added `GatewayAdminProxyForwardingTests` with an isolated HTTP upstream. The test verifies the
  admin request method, path, query, JSON body, `Authorization`, normalized `X-Trace-Id`,
  `Idempotency-Key`, removal of caller-controlled `X-Actor-Id`, downstream status/body, and a
  downstream response header.
- The test uses a test-only reactive JWT decoder for `Bearer test-admin-token`; it does not claim
  real authentication-service JWKS verification. That remains governed by the separate
  Authentication feature prerequisite recorded by T066.
- Repaired the stale `GatewaySecurityErrorHandler` constructor/behavior mismatch exposed during
  test compilation by restoring the approved Feature 011 authentication and access-denial
  classification through `GatewayFailureClassifier`.
- Validation: `./mvnw.cmd -pl services/api-gateway -am -Dtest=GatewayAdminProxyForwardingTests
  -Dsurefire.failIfNoSpecifiedTests=false test` -> PASS; 1 test, 0 failures, 0 errors, 0 skipped;
  finished 2026-07-24.
- Clean follow-up validation: `./mvnw.cmd -pl services/api-gateway -am clean verify` -> PASS;
  all current Gateway test classes passed with 0 failures, 0 errors, and 0 skipped; stale compiled
  test artifacts were removed before verification.
- CI/PR reference: N/A for this local implementation run; no remote PR operation was in scope.

---

## Phase 9: Convergence

**Purpose**: Remove unrequested Java placeholders from the lean gateway profile and make the
already-approved Product Admin edge errors type-safe without changing their HTTP contract or adding
future rate-limit, circuit-breaker, routing, upstream, or global-error behavior.

- [X] T070 Replace `GatewayAdminErrorResponse` with gateway-owned `GatewayErrorResponse`, implement
  `GatewayErrorCode` for only `INVALID_ADMIN_REQUEST`, `UNAUTHENTICATED`, and
  `CATALOG_ADMIN_REQUIRED`, update `GatewayHttpErrorWriter`, `GatewaySecurityErrorHandler`, and
  `AdminCatalogRequestBoundaryFilter` to use the typed codes while preserving the approved
  `{code,message,traceId}` body/status/message behavior; remove unrequested empty Java placeholders
  from `services/api-gateway/src/main/java/com/philia/flashsale/gateway/`, retain approved empty
  technical packages with `.gitkeep`, add blank/128-character trace boundary coverage in
  `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ProductAdminGatewayRouteTests.java`,
  and run `.\mvnw.cmd -pl services/api-gateway -am clean verify` per the Product Admin HTTP error
  contract, plan gateway error ownership, and ADR 0003 (unrequested)

**Approval**: Product owner explicitly requested the gateway error-code inventory and production
cleanup in chat on 2026-07-22. This approval is limited to the three Feature 010 codes above and
does not approve a global gateway error taxonomy or future operational policies.

**Completion evidence (2026-07-22)**:

- Validation: `.\\mvnw.cmd -q -pl services/api-gateway -am clean verify`
- Scope: Gateway error contract refactor plus the api-gateway reactor dependencies.
- Result: exit code `0`; 10 Gateway tests passed (`2` application tests and `8` route/boundary tests),
  with no failures, errors, or skipped tests.
- Follow-up validation (2026-07-22): rewrote both security-handler callers explicitly as
  `write(exchange, GatewayErrorCode)` to invalidate stale editor diagnostics; the same clean verify
  command returned exit code `0` with all 10 Gateway tests passing.
