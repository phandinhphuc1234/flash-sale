---
description: "Dependency-ordered implementation tasks for the Clean/Hexagonal service scaffold"
---

# Tasks: Clean Hexagonal Service Scaffold

**Input**: Design documents from `specs/002-clean-hex-scaffold/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/service-structure.md`, and `quickstart.md`

**Status**: Approved

**Tests**: Static scaffold inspection is required. Existing Maven context tests are the relevant runtime regression check because this feature adds marker files and documentation only.

**Organization**: Tasks are grouped by user story and executed phase by phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after prior-phase prerequisites complete
- **[Story]**: Maps the task to `US1`, `US2`, or `US3`
- Every task includes an exact repository path or validation artifact

## Phase 1: Setup (Spec Kit Artifacts)

**Purpose**: Create the active feature artifacts before touching service structure.

- [x] T001 Create the feature specification in `specs/002-clean-hex-scaffold/spec.md`
- [x] T002 Create the requirements checklist in `specs/002-clean-hex-scaffold/checklists/requirements.md`
- [x] T003 Create the implementation plan in `specs/002-clean-hex-scaffold/plan.md`
- [x] T004 Create research, data model, contract, and quickstart artifacts under `specs/002-clean-hex-scaffold/`
- [x] T005 Update `.specify/feature.json` to point to `specs/002-clean-hex-scaffold`

---

## Phase 2: Foundational (Architecture Guidance)

**Purpose**: Document dependency direction and port naming before adding package markers.

- [x] T006 [P] [US2] Create the repository architecture guide in `docs/architecture/service-clean-hex-structure.md`
- [x] T007 [US2] Confirm `specs/002-clean-hex-scaffold/contracts/service-structure.md` rejects technology-shaped ports and reverse dependencies

**Checkpoint**: Future implementation rules are visible before package scaffold work starts.

---

## Phase 3: User Story 1 - Navigate Service Architecture Consistently (Priority: P1) MVP

**Goal**: Every service has visible architecture zones without business implementation classes.

**Independent Test**: Inspect each service tree and confirm only marker files were added.

### Implementation for User Story 1

- [x] T008 [P] [US1] Add lean Clean/Hex scaffold markers under `services/api-gateway/src/main/java/com/philia/flashsale/gateway/`
- [x] T009 [P] [US1] Add Clean/Hex scaffold markers under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/`
- [x] T010 [P] [US1] Add Clean/Hex scaffold markers under `services/product-service/src/main/java/com/philia/flashsale/product/`
- [x] T011 [P] [US1] Add Clean/Hex scaffold markers under `services/campaign-service/src/main/java/com/philia/flashsale/campaign/`
- [x] T012 [P] [US1] Add full Clean/Hex scaffold markers under `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/`
- [x] T013 [P] [US1] Add Clean/Hex scaffold markers under `services/order-service/src/main/java/com/philia/flashsale/order/`
- [x] T014 [P] [US1] Add Clean/Hex scaffold markers under `services/payment-service/src/main/java/com/philia/flashsale/payment/`
- [x] T015 [P] [US1] Add Clean/Hex scaffold markers under `services/notification-service/src/main/java/com/philia/flashsale/notification/`
- [x] T016 [P] [US1] Add Clean/Hex scaffold markers under `services/chatting-service/src/main/java/com/philia/flashsale/chatting/`

**Checkpoint**: User Story 1 provides a visible scaffold for every service.

---

## Phase 4: User Story 3 - See the Spec Kit Development Flow (Priority: P3)

**Goal**: The feature artifacts and architecture guide show the repeatable Spec Kit flow.

**Independent Test**: Review the feature directory and architecture guide.

### Implementation for User Story 3

- [x] T017 [US3] Verify `specs/002-clean-hex-scaffold/` contains spec, plan, research, data model, contract, quickstart, checklist, and tasks artifacts
- [x] T018 [US3] Verify `docs/architecture/service-clean-hex-structure.md` explains specify, plan, tasks, implement, validate, and converge flow

---

## Phase 5: Polish and Cross-Cutting Validation

**Purpose**: Confirm the scaffold stayed inside the approved scope.

- [x] T019 Inspect added files and confirm no new production Java implementation classes were introduced
- [x] T020 Inspect existing service POMs and application configuration remain unchanged by this feature
- [x] T021 Run `.\mvnw.cmd clean verify` when the local shell is available, or record the environment blocker

---

## Dependencies and Execution Order

```text
Setup -> Architecture guidance -> US1 scaffold -> US3 workflow visibility -> Validation
```

## Implementation Strategy

1. Create and approve the feature artifacts.
2. Document the architectural rules.
3. Add marker files only.
4. Inspect for accidental production classes.
5. Run Maven verification when the local environment permits.
