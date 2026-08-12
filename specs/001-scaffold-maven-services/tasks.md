---
description: "Dependency-ordered implementation tasks for the Maven microservice skeleton"
---

# Tasks: Maven Multi-Module Microservice Skeleton

**Input**: Design documents from `specs/001-scaffold-maven-services/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/operational-endpoints.md`, and `quickstart.md`

**Status**: Approved

**Tests**: Nine context-load tests, isolated module builds, a full reactor build, dependency/boundary
inspection, and runtime operational-endpoint smoke checks are required. Database, Kafka, Redis,
business-contract, load, and Kubernetes tests are out of scope because no corresponding behavior or
manifest is introduced.

**Organization**: Tasks are grouped by user story and executed phase by phase. Parallel markers apply
only to files or validations that do not share an incomplete prerequisite.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after prior-phase prerequisites complete
- **[Story]**: Maps the task to `US1`, `US2`, or `US3`
- Every task includes an exact repository path or exact validation artifact

## Phase 1: Setup (Shared Build Infrastructure)

**Purpose**: Establish the root reactor, dependency/plugin management, module POMs, and repository
ignore rules before adding application sources.

- [x] T001 Create the Java 21 parent/aggregator with nine modules, Spring Boot 3.5.16 parent, Spring Cloud 2025.0.3 BOM, UTF-8 properties, and plugin management in `pom.xml`
- [x] T002 Append essential Java/Maven and universal generated-file patterns without removing existing content in `.gitignore`
- [x] T003 [P] Create the Gateway WebFlux, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/api-gateway/pom.xml`
- [x] T004 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/authentication-service/pom.xml`
- [x] T005 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/product-service/pom.xml`
- [x] T006 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/campaign-service/pom.xml`
- [x] T007 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/flashsale-service/pom.xml`
- [x] T008 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/order-service/pom.xml`
- [x] T009 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/payment-service/pom.xml`
- [x] T010 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/notification-service/pom.xml`
- [x] T011 [P] Create the Spring Web, Actuator, runtime Prometheus, and test dependencies plus executable-JAR plugin declaration in `services/chatting-service/pom.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Make the new reactor reproducible before implementing any user story.

**Critical**: No user-story task begins until this phase completes.

- [x] T012 Generate the official root-only Maven Wrapper with `mvn wrapper:wrapper -Dtype=bin`, producing `.mvn/wrapper/maven-wrapper.properties`, `.mvn/wrapper/maven-wrapper.jar`, `mvnw`, and `mvnw.cmd`
- [x] T013 Validate the nine-module Maven model and inherited framework/plugin versions through root `pom.xml` and `.mvn/wrapper/maven-wrapper.properties`

**Checkpoint**: One reproducible root reactor exists and all module build descriptors resolve.

---

## Phase 3: User Story 1 - Build the Complete Service Skeleton (Priority: P1) MVP

**Goal**: Build nine independently executable application artifacts from one root command with one
enabled context-load test per service and no direct service-to-service dependency.

**Independent Test**: Run `.\mvnw.cmd clean verify` from the root and confirm nine modules, nine tests,
nine executable JARs, and `BUILD SUCCESS`.

### Tests for User Story 1

- [x] T014 [P] [US1] Add the failing-first context-load test in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ApiGatewayApplicationTests.java`
- [x] T015 [P] [US1] Add the failing-first context-load test in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/AuthenticationServiceApplicationTests.java`
- [x] T016 [P] [US1] Add the failing-first context-load test in `services/product-service/src/test/java/com/philia/flashsale/product/ProductServiceApplicationTests.java`
- [x] T017 [P] [US1] Add the failing-first context-load test in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/CampaignServiceApplicationTests.java`
- [x] T018 [P] [US1] Add the failing-first context-load test in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/FlashsaleServiceApplicationTests.java`
- [x] T019 [P] [US1] Add the failing-first context-load test in `services/order-service/src/test/java/com/philia/flashsale/order/OrderServiceApplicationTests.java`
- [x] T020 [P] [US1] Add the failing-first context-load test in `services/payment-service/src/test/java/com/philia/flashsale/payment/PaymentServiceApplicationTests.java`
- [x] T021 [P] [US1] Add the failing-first context-load test in `services/notification-service/src/test/java/com/philia/flashsale/notification/NotificationServiceApplicationTests.java`
- [x] T022 [P] [US1] Add the failing-first context-load test in `services/chatting-service/src/test/java/com/philia/flashsale/chatting/ChattingServiceApplicationTests.java`
- [x] T023 [US1] Run the User Story 1 tests through root `pom.xml` and confirm they fail before the application classes exist

### Implementation for User Story 1

- [x] T024 [P] [US1] Create the Gateway application entry point in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ApiGatewayApplication.java`
- [x] T025 [P] [US1] Create the authentication application entry point in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/AuthenticationServiceApplication.java`
- [x] T026 [P] [US1] Create the product application entry point in `services/product-service/src/main/java/com/philia/flashsale/product/ProductServiceApplication.java`
- [x] T027 [P] [US1] Create the campaign application entry point in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/CampaignServiceApplication.java`
- [x] T028 [P] [US1] Create the flash-sale application entry point in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/FlashsaleServiceApplication.java`
- [x] T029 [P] [US1] Create the order application entry point in `services/order-service/src/main/java/com/philia/flashsale/order/OrderServiceApplication.java`
- [x] T030 [P] [US1] Create the payment application entry point in `services/payment-service/src/main/java/com/philia/flashsale/payment/PaymentServiceApplication.java`
- [x] T031 [P] [US1] Create the notification application entry point in `services/notification-service/src/main/java/com/philia/flashsale/notification/NotificationServiceApplication.java`
- [x] T032 [P] [US1] Create the chatting application entry point in `services/chatting-service/src/main/java/com/philia/flashsale/chatting/ChattingServiceApplication.java`
- [x] T033 [US1] Build and test all application shells through root `pom.xml` with `.\mvnw.cmd clean verify`

**Checkpoint**: User Story 1 independently produces nine executable, tested skeleton artifacts.

---

## Phase 4: User Story 2 - Start and Inspect Each Service Independently (Priority: P2)

**Goal**: Give every service its correct identity, configurable port, health probes, and
Prometheus-compatible operational endpoint through declarative configuration only.

**Independent Test**: Start each service sequentially with a unique `SERVER_PORT` and validate all five
paths documented in `specs/001-scaffold-maven-services/contracts/operational-endpoints.md`.

### Implementation for User Story 2

- [x] T034 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/api-gateway/src/main/resources/application.yml`
- [x] T035 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/authentication-service/src/main/resources/application.yml`
- [x] T036 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/product-service/src/main/resources/application.yml`
- [x] T037 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/campaign-service/src/main/resources/application.yml`
- [x] T038 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/flashsale-service/src/main/resources/application.yml`
- [x] T039 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/order-service/src/main/resources/application.yml`
- [x] T040 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/payment-service/src/main/resources/application.yml`
- [x] T041 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/notification-service/src/main/resources/application.yml`
- [x] T042 [P] [US2] Configure application identity, `${SERVER_PORT:8080}`, health/info/prometheus exposure, and health probes in `services/chatting-service/src/main/resources/application.yml`
- [x] T043 [US2] Run sequential service startup and endpoint smoke checks against `specs/001-scaffold-maven-services/contracts/operational-endpoints.md`, asserting service identity and healthy readiness within 60 seconds

**Checkpoint**: User Story 2 proves each service can start and expose the uniform operational contract.

---

## Phase 5: User Story 3 - Preserve a Minimal, Bounded Foundation (Priority: P3)

**Goal**: Prove the scaffold contains only approved application-shell concerns and preserves all
pre-existing content.

**Independent Test**: Inspect the source tree, POM dependency allow-list, wrapper locations, and Git
diff against the pre-change baseline.

### Verification for User Story 3

- [x] T044 [US3] Audit all production and test source paths under `services/` and confirm only the approved application class, `application.yml`, and context test exist per module
- [x] T045 [US3] Audit dependency allow-lists and confirm zero service-module dependencies across `pom.xml` and all nine `services/*/pom.xml` files
- [x] T046 [US3] Audit `git diff`, root `src/`, `libs/`, service-local wrapper paths, `docs/`, `infra/`, `load-tests/`, and `scripts/` to confirm scope preservation

**Checkpoint**: User Story 3 confirms the delivered foundation has no premature business or
infrastructure implementation.

---

## Phase 6: Polish and Cross-Cutting Validation

**Purpose**: Execute every required module, reactor, dependency, and quickstart quality gate.

- [x] T047 [P] Verify `services/api-gateway/pom.xml` in isolation with `.\mvnw.cmd -pl services/api-gateway -am verify`
- [x] T048 [P] Verify `services/authentication-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/authentication-service -am verify`
- [x] T049 [P] Verify `services/product-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/product-service -am verify`
- [x] T050 [P] Verify `services/campaign-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/campaign-service -am verify`
- [x] T051 [P] Verify `services/flashsale-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/flashsale-service -am verify`
- [x] T052 [P] Verify `services/order-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/order-service -am verify`
- [x] T053 [P] Verify `services/payment-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/payment-service -am verify`
- [x] T054 [P] Verify `services/notification-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/notification-service -am verify`
- [x] T055 [P] Verify `services/chatting-service/pom.xml` in isolation with `.\mvnw.cmd -pl services/chatting-service -am verify`
- [x] T056 Run the final full-reactor quality gate from `pom.xml` with `.\mvnw.cmd clean verify` and require nine passing tests plus `BUILD SUCCESS`
- [x] T057 Inspect resolved dependency trees from all nine `services/*/pom.xml` files and require zero direct service dependencies and zero prohibited libraries
- [x] T058 Execute every applicable scenario and expected outcome in `specs/001-scaffold-maven-services/quickstart.md`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 Setup** has no prerequisite; T001 and T002 precede the module POM tasks.
- **Phase 2 Foundational** depends on every Setup task and blocks all user stories.
- **User Story 1** depends on Foundational; its tests are written and observed failing before main
  classes are added.
- **User Story 2** depends on User Story 1 because it configures and starts those application shells.
- **User Story 3** depends on User Stories 1 and 2 because it audits their completed scope.
- **Polish** depends on all three user stories and is the final completion gate.

### User Story Dependency Graph

```text
Setup -> Foundational -> US1 (build) -> US2 (operate) -> US3 (scope audit) -> Polish
```

### Parallel Opportunities

- T003-T011 operate on different module POMs after T001.
- T014-T022 create different test files.
- T024-T032 create different main classes.
- T034-T042 create different configuration files.
- T047-T055 validate different module targets after the shared wrapper and dependencies are cached.

## Parallel Examples

### User Story 1

```text
Task T014: services/api-gateway/.../ApiGatewayApplicationTests.java
Task T015: services/authentication-service/.../AuthenticationServiceApplicationTests.java
Task T016: services/product-service/.../ProductServiceApplicationTests.java

After the red test checkpoint:

Task T024: services/api-gateway/.../ApiGatewayApplication.java
Task T025: services/authentication-service/.../AuthenticationServiceApplication.java
Task T026: services/product-service/.../ProductServiceApplication.java
```

### User Story 2

```text
Task T034: services/api-gateway/src/main/resources/application.yml
Task T035: services/authentication-service/src/main/resources/application.yml
Task T036: services/product-service/src/main/resources/application.yml
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational phases.
2. Complete User Story 1 tests, red checkpoint, main classes, and reactor verification.
3. Stop if the MVP build fails; do not mask or skip tests.

### Incremental Delivery

1. Add declarative operational configuration and smoke-test User Story 2.
2. Audit exclusions and repository preservation for User Story 3.
3. Run all isolated and full-reactor quality gates.

## Notes

- Mark a task `[x]` only after its required file or validation is complete.
- Tasks affecting the same file execute sequentially even when neighboring tasks are parallelizable.
- No controller, DTO, service, repository, entity, route, filter, security configuration, database,
  message broker, cache, WebSocket, mail, Docker, Kubernetes, or fake-data artifact belongs in this
  task list.
- Stop and report the actual blocker if wrapper generation, dependency resolution, tests, startup, or
  endpoint validation fails.
