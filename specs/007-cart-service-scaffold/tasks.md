# Tasks: Cart Service Scaffold

**Input**: Design documents from `specs/007-cart-service-scaffold/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/cart-service-scaffold.md`, and accepted ADR 0002

**Tests**: This setup-only feature uses risk-based ordering. The Cart application-context test, exact negative scope audits, module build, full reactor build, and both Compose renders are required. No Cart business test is applicable because no business behavior is approved.

**Organization**: Tasks are grouped by independently testable user story. Production module implementation cannot begin until ADR 0002 is accepted.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it targets different files and has no incomplete dependency.
- **[Story]**: Maps the task to `US1`, `US2`, or `US3` from `spec.md`.
- Every task names its repository path.

## Phase 1: Setup and Governance

**Purpose**: Satisfy the mandatory service-boundary gate before production implementation.

- [x] T001 Record the accepted Cart ownership decision, alternatives, consequences, migration impact, and rollback in `docs/adr/0002-cart-service-boundary.md`

**Checkpoint**: ADR 0002 is accepted; module implementation is permitted by the Constitution and approved plan.

---

## Phase 2: Foundational Module Registration

**Purpose**: Register the independent module and its approved dependency boundary.

- [x] T002 Add `services/cart-service` exactly once to the Maven reactor in `pom.xml`
- [x] T003 Create the ordinary business-service Maven baseline with only approved dependencies in `services/cart-service/pom.xml`
- [x] T004 Create the 17 empty canonical Clean/Hexagonal marker paths under `services/cart-service/src/main/java/com/philia/flashsale/cart/`

**Checkpoint**: The reactor and package ownership are defined without business behavior.

---

## Phase 3: User Story 1 - Independent Cart Service Shell (Priority: P1) MVP

**Goal**: Deliver an independently buildable Spring Boot Cart shell with the same minimal operational and migration baseline as ordinary business services.

**Independent Test**: Run `.\mvnw.cmd -pl services/cart-service -am verify` and confirm the exact Java and marker allowlists.

### Test and implementation

- [x] T005 [P] [US1] Add the `CartServiceApplication` Spring Boot entry point in `services/cart-service/src/main/java/com/philia/flashsale/cart/CartServiceApplication.java`
- [x] T006 [P] [US1] Add the application-context test in `services/cart-service/src/test/java/com/philia/flashsale/cart/CartServiceApplicationTests.java`
- [x] T007 [P] [US1] Configure application identity, internal port, liveness, readiness, info, and Prometheus exposure declaratively in `services/cart-service/src/main/resources/application.yml`
- [x] T008 [P] [US1] Add only the empty Liquibase master and empty changes marker in `services/cart-service/src/main/resources/db/changelog/`
- [x] T009 [US1] Audit `services/cart-service/` for exactly two Java files, 17 empty architecture markers, the approved dependency allowlist, an empty changelog, and no business class, route, persistence, messaging, Redis, or manual registry code (PASS: exact allowlists and all negative scans)
- [x] T010 [US1] Run `.\mvnw.cmd -pl services/cart-service -am verify` and record successful Cart context/build evidence in `specs/007-cart-service-scaffold/tasks.md` (PASS: 1 test, 0 failures/errors, module BUILD SUCCESS; runtime smoke returned HTTP 200 with non-empty bodies for all five operational endpoints)

**Checkpoint**: User Story 1 is independently complete and the Cart shell builds without implementing Cart behavior.

---

## Phase 4: User Story 2 - Local Container Topology (Priority: P2)

**Goal**: Integrate the shell into the existing root-owned Compose workflow and reserve its empty local database boundary.

**Independent Test**: Render base and development Compose configurations and confirm internal-only base exposure plus development port `18089`.

### Implementation and validation

- [x] T011 [P] [US2] Add the Java 21 multi-stage non-root image recipe in `services/cart-service/Dockerfile`
- [x] T012 [P] [US2] Provision only logical database `cart_db` in `infra/docker/postgres/init/01-create-databases.sql`
- [x] T013 [P] [US2] Add Cart image and debug-port defaults in `infra/docker/.env.example`
- [x] T014 [US2] Add internal-only `cart-service` using the shared business-service baseline in `infra/docker/compose.yml`
- [x] T015 [US2] Add configurable `${CART_SERVICE_PORT:-18089}:8080` development exposure in `infra/docker/compose.dev.yml`
- [x] T016 [P] [US2] Document Cart DNS, debug port, `cart_db`, and existing-volume initialization behavior in `infra/docker/README.md`
- [x] T017 [US2] Render and validate the base `apps` topology from `infra/docker/compose.yml` with `infra/docker/.env.example` (PASS: cart exposes only internal `8080`; no published host port)
- [x] T018 [US2] Render and validate the development `apps` topology from `infra/docker/compose.yml` plus `infra/docker/compose.dev.yml` (PASS: `18089 -> 8080`)

**Checkpoint**: User Story 2 is independently complete; Compose knows Cart but base traffic still cannot bypass the API Gateway convention.

---

## Phase 5: User Story 3 - Reviewable Boundary and Topology (Priority: P3)

**Goal**: Make the current Cart boundary, service count, deployment shape, and deliberately deferred behavior unambiguous.

**Independent Test**: Review all changed living documents and obtain one consistent ten-service topology without any claimed Cart business route or event.

### Documentation

- [x] T019 [P] [US3] Update the current service count, repository tree, and ADR link in `README.md`
- [x] T020 [P] [US3] Update the nine-business-service baseline and Cart placement guidance in `docs/architecture/service-clean-hex-structure.md`
- [x] T021 [US3] Add a current Cart shell and clearly planned database edge, without a current gateway/protocol edge, in `docs/architecture/diagrams/system-overview.mmd` and `docs/architecture/diagrams/system-overview.md`
- [x] T022 [P] [US3] Update Cart local topology and future Kubernetes skeleton guidance in `docs/deployment/container-compose-k8s-strategy.md` and `infra/k8s/README.md`
- [x] T023 [P] [US3] Add Cart future durable-state ownership without inventing a schema in `docs/technology/technology-problem-map.md`
- [x] T024 [US3] Audit `README.md`, `docs/`, and `infra/` for a consistent ten-service/nine-business-service topology, correct `cart-service`/`cart_db` naming, and no claimed Cart business contract (PASS: no stale living-doc counts or Cart protocol edge)

**Checkpoint**: User Story 3 is complete; reviewers can understand both the accepted boundary and the setup-only limitation.

---

## Phase 6: Final Cross-Cutting Verification

**Purpose**: Prove governance, scope, regression safety, and completion across the monorepo.

- [x] T025 Validate the approved feature pointer/spec/plan and accepted ADR status in `.specify/feature.json`, `specs/007-cart-service-scaffold/spec.md`, `specs/007-cart-service-scaffold/plan.md`, and `docs/adr/0002-cart-service-boundary.md` (PASS)
- [x] T026 Re-run exact reactor, Java, marker, dependency, gateway-route, contract, migration, and compiled-class scope audits across `pom.xml`, `services/cart-service/`, `services/api-gateway/src/main/`, and `specs/007-cart-service-scaffold/contracts/` (PASS)
- [x] T027 Run `.\mvnw.cmd clean verify` and confirm successful verification for all ten service modules (PASS: 11-project reactor including parent; all ten services SUCCESS)
- [x] T028 Execute every applicable validation in `specs/007-cart-service-scaffold/quickstart.md` and record final evidence in `specs/007-cart-service-scaffold/tasks.md` (PASS)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** has no implementation dependency and is the architectural gate.
- **Phase 2** depends on accepted ADR task T001.
- **User Story 1** depends on the registered module and package foundation in Phase 2.
- **User Story 2** depends on the Cart module existing; its independent files marked `[P]` can be prepared together before Compose integration.
- **User Story 3** depends on the accepted ADR and final topology decisions, but its different living documents can be updated in parallel.
- **Final verification** depends on all selected user stories being complete.

### User Story Dependencies

- **US1 (P1)**: No dependency on US2 or US3; it is the independently buildable MVP.
- **US2 (P2)**: Requires the US1 module paths so its Dockerfile and Compose build target are valid; it can be tested without US3.
- **US3 (P3)**: Documents US1/US2 outcomes but introduces no runtime dependency.

### Parallel Opportunities

- T003 and T004 target independent module artifacts after T002.
- T005 through T008 target independent source/config/test/migration files.
- T011 through T013 and T016 target independent container/provisioning/document files.
- T019, T020, T022, and T023 target different living documents.

## Implementation Strategy

1. Accept ADR 0002 and confirm the approved Plan gate.
2. Register the module and dependency boundary.
3. Complete and independently validate US1 as the MVP.
4. Integrate and render the local topology for US2.
5. Update and audit living documents for US3.
6. Run exact negative scope checks and the full reactor verification.

## Notes

- Empty directories are represented by zero-byte `.gitkeep` files.
- Existing features `001` through `006` are historical evidence and remain unchanged.
- No task authorizes Cart business behavior. The first Cart API/schema/use case must begin as a new Spec Kit feature.
- A PostgreSQL bootstrap script does not rerun for an existing volume; validation must not remove local data.

## Validation Evidence

- **Governance**: `.specify/feature.json` selects feature 007; the specification and plan are Approved; ADR 0002 is Accepted and contains Context, Decision, Consequences, Migration Impact, and Alternatives considered.
- **Cart module**: `.\mvnw.cmd -pl services/cart-service -am verify` completed with BUILD SUCCESS; one Cart context test ran with zero failures or errors.
- **Operational smoke**: the packaged Cart JAR ran on Java 21 and returned HTTP 200 with non-empty bodies for health, liveness, readiness, info, and Prometheus; the Prometheus response was 13,921 bytes.
- **Compose**: base and development `apps` renders passed. Base Cart exposes internal `8080` with no host publication; development maps `18089 -> 8080`.
- **Exact inventories**: ten Maven service modules; 20 Java sources repository-wide (10 production and 10 tests); 164 Java-zone markers; 10 compiled production classes; 10 Surefire reports. Cart has exactly two Java sources, 17 empty architecture markers, one empty Liquibase marker, and five approved dependencies.
- **Negative scope**: no Cart controller/API/gateway route, DTO/use case/domain model, JPA/repository/table/changeset, Kafka/Redis/gRPC/client dependency, service-module dependency, business contract, or manual Prometheus registry code was found.
- **Full regression**: `.\mvnw.cmd clean verify` completed with BUILD SUCCESS for the parent plus all ten service modules.
