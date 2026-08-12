# Tasks: Container Compose and Kubernetes Readiness

**Input**: Design documents from `/specs/005-container-compose-k8s-readiness/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/container-runtime.md, quickstart.md

**Tests**: Required validation is Compose configuration rendering for baseline and development override. Maven verification is not required because Java source and POM dependencies are unchanged.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup

- [x] T001 Update `.gitignore` so real `.env` files remain ignored while `.env.example` files can be committed
- [x] T002 Update `.dockerignore` for Java monorepo Docker build context hygiene
- [x] T003 [P] Create deployment docs index in `docs/deployment/README.md`

---

## Phase 2: Foundational

- [x] T004 Create shared local environment example in `infra/docker/.env.example`
- [x] T005 Create local PostgreSQL database bootstrap in `infra/docker/postgres/init/01-create-databases.sql`
- [x] T006 Create base Compose topology in `infra/docker/compose.yml`
- [x] T007 Create development Compose override in `infra/docker/compose.dev.yml`

---

## Phase 3: User Story 1 - Run a local platform baseline (Priority: P1) 🎯 MVP

**Goal**: Developers can validate and start shared local platform dependencies from `infra/docker`.

**Independent Test**: `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config`

- [x] T008 [US1] Document platform-only usage in `infra/docker/README.md`
- [x] T009 [US1] Validate baseline Compose rendering with `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config`

---

## Phase 4: User Story 2 - Build services as independently deployable images (Priority: P2)

**Goal**: Every service has a service-local Dockerfile and the app profile can render all service containers.

**Independent Test**: Every `services/<service>/Dockerfile` exists and `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config` succeeds.

- [x] T010 [P] [US2] Add Dockerfile in `services/api-gateway/Dockerfile`
- [x] T011 [P] [US2] Add Dockerfile in `services/authentication-service/Dockerfile`
- [x] T012 [P] [US2] Add Dockerfile in `services/product-service/Dockerfile`
- [x] T013 [P] [US2] Add Dockerfile in `services/campaign-service/Dockerfile`
- [x] T014 [P] [US2] Add Dockerfile in `services/flashsale-service/Dockerfile`
- [x] T015 [P] [US2] Add Dockerfile in `services/order-service/Dockerfile`
- [x] T016 [P] [US2] Add Dockerfile in `services/payment-service/Dockerfile`
- [x] T017 [P] [US2] Add Dockerfile in `services/notification-service/Dockerfile`
- [x] T018 [P] [US2] Add Dockerfile in `services/chatting-service/Dockerfile`
- [x] T019 [US2] Validate development override Compose rendering with `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config`

---

## Phase 5: User Story 3 - Understand the Kubernetes migration path (Priority: P3)

**Goal**: Developers can understand how Compose maps to future Kubernetes and why no `compose.prod.yml` is introduced.

**Independent Test**: Documentation explains Dockerfile ownership, Compose layering, no-production-Compose decision, K8s mapping, ConfigMap/Secret mapping, probes, and migration jobs.

- [x] T020 [US3] Add deployment strategy docs in `docs/deployment/container-compose-k8s-strategy.md`
- [x] T021 [US3] Update `infra/k8s/README.md` with future base/overlay expansion guidance
- [x] T022 [US3] Update `infra/README.md` to reflect implemented Docker local orchestration
- [x] T023 [US3] Update root `README.md` with Docker Compose quickstart and current infrastructure status
- [x] T024 [US3] Update `docs/technology/README.md` and `docs/technology/technology-problem-map.md` with containerization status

---

## Phase 6: Polish & Cross-Cutting

- [x] T025 Run `rg --files -g Dockerfile services` to verify all service Dockerfiles exist
- [x] T026 Run `rg --line-number "compose.prod|LIQUIBASE_ENABLED|1808|api-gateway" infra docs specs/005-container-compose-k8s-readiness` to audit key decisions
- [x] T027 Record final validation status in `specs/005-container-compose-k8s-readiness/tasks.md`

## Dependencies & Execution Order

- Setup tasks must complete before Compose files and docs are finalized.
- Foundational tasks block US1 and US2.
- US1 can validate platform topology before service Dockerfiles are built.
- US2 depends on the base Compose topology and service Dockerfiles.
- US3 can be completed after the implementation shape is known.

## Implementation Strategy

1. Create ignore/env/bootstrap files.
2. Add Compose topology and dev override.
3. Add service Dockerfiles.
4. Write docs and Kubernetes expansion guidance.
5. Validate Compose rendering and static file expectations.
