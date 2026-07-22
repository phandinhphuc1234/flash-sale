---
description: "Dependency-ordered tasks for the Clean Architecture working baseline"
---

# Tasks: Clean Architecture Working Baseline

**Input**: Approved artifacts from `specs/006-clean-architecture-baseline/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/package-placement.md`, `quickstart.md`, and `checklists/requirements.md`

**Approval gate**: The feature is setup-only, contains no blocking clarification, and explicitly excludes Product Catalog behavior.

**Tests**: Static topology, documentation, exact Java allowlist, prohibited Product behavior scans, and the full Maven reactor build are required.

## Format: `[ID] [P?] [Story] Description (trace references)`

- **[P]**: Safe to run in parallel because files do not overlap.
- **[Story]**: Maps the task to an independently testable user story.
- Every task includes an exact repository path and its expected evidence.

## Phase 1: Setup and Artifact Readiness

- [x] T001 Confirm the approved scope and active pointer in `specs/006-clean-architecture-baseline/spec.md`, `specs/006-clean-architecture-baseline/plan.md`, and `.specify/feature.json` (FR-008-FR-010)
- [x] T002 [P] Confirm every item passes in `specs/006-clean-architecture-baseline/checklists/requirements.md` (FR-008-FR-010)
- [x] T003 [P] Record the pre-change Java allowlist baseline from `services/**/*.java` in the Evidence Record below (FR-008/FR-009)

**Checkpoint**: Approved scope and the no-business-code baseline are explicit before service-tree changes.

---

## Phase 2: User Story 1 - Start a Service Slice from a Consistent Baseline (Priority: P1) MVP

**Goal**: The eight business services have a visible query-input location while the gateway stays lean.

**Independent Test**: Eight empty `application/query/.gitkeep` markers exist under business-service context packages and no equivalent gateway marker exists.

- [x] T004 [P] [US1] Add `services/authentication-service/src/main/java/com/philia/flashsale/authentication/application/query/.gitkeep` (FR-001/FR-002)
- [x] T005 [P] [US1] Add `services/product-service/src/main/java/com/philia/flashsale/product/application/query/.gitkeep` without adding Product behavior (FR-001/FR-002/FR-009)
- [x] T006 [P] [US1] Add `services/campaign-service/src/main/java/com/philia/flashsale/campaign/application/query/.gitkeep` (FR-001/FR-002)
- [x] T007 [P] [US1] Add `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/application/query/.gitkeep` (FR-001/FR-002)
- [x] T008 [P] [US1] Add `services/order-service/src/main/java/com/philia/flashsale/order/application/query/.gitkeep` (FR-001/FR-002)
- [x] T009 [P] [US1] Add `services/payment-service/src/main/java/com/philia/flashsale/payment/application/query/.gitkeep` (FR-001/FR-002)
- [x] T010 [P] [US1] Add `services/notification-service/src/main/java/com/philia/flashsale/notification/application/query/.gitkeep` (FR-001/FR-002)
- [x] T011 [P] [US1] Add `services/chatting-service/src/main/java/com/philia/flashsale/chatting/application/query/.gitkeep` (FR-001/FR-002)
- [x] T012 [US1] Validate the eight markers are empty and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/application/query/.gitkeep` remains absent; record evidence below (FR-002/FR-003)

**Checkpoint**: US1 is complete without a Java class or gateway responsibility change.

---

## Phase 3: User Story 2 - Place Common Java Concerns without Breaking Boundaries (Priority: P2)

**Goal**: Developers have one practical, create-on-demand placement guide and reviewer contract.

**Independent Test**: The guide and contract correctly classify all FR-004 concerns, preserve inward dependencies, and reject global technical dumping grounds.

- [x] T013 [US2] Expand `docs/architecture/service-clean-hex-structure.md` with the canonical business/gateway trees, create-on-demand rule, boundary-model mapping, error/validation rules, persistence rules, configuration/security/scheduling placement, and test placement (FR-004-FR-006)
- [x] T014 [P] [US2] Finalize the normative reviewer rules in `specs/006-clean-architecture-baseline/contracts/package-placement.md` (FR-004-FR-006)
- [x] T015 [US2] Add a discoverable Clean/Hexagonal guide link to `README.md` (FR-007)
- [x] T016 [US2] Audit `docs/architecture/service-clean-hex-structure.md`, `specs/006-clean-architecture-baseline/contracts/package-placement.md`, and `README.md` for all required placement and dependency-direction wording; record evidence below (FR-004-FR-007)

**Checkpoint**: US2 is independently usable as the source-placement reference for future feature planning.

---

## Phase 4: User Story 3 - Preserve the Empty Business Baseline (Priority: P3)

**Goal**: Prove that setup did not begin Product Catalog or any other runtime feature.

**Independent Test**: The source inventory remains exactly 18 approved Java files and static scans find no prohibited endpoint, route, persistence, contract, migration, or dependency addition.

- [x] T017 [US3] Validate the exact 18-file Java allowlist under `services/` and record evidence below (FR-008/FR-009/SC-003)
- [x] T018 [US3] Scan `services/`, `contracts/`, and service POM/config/migration paths for Product controllers, `GET` mappings, use cases, routes, entities, repositories, business changesets, and newly introduced runtime dependencies; record evidence below (FR-008-FR-010)

**Checkpoint**: US3 proves that Product Catalog behavior remains available for a future independently planned feature.

---

## Phase 5: Cross-Cutting Verification

- [x] T019 Run `.\mvnw.cmd clean verify` from the repository root and record the nine-service reactor result below (FR-011/SC-004)
- [x] T020 Run every static scenario in `specs/006-clean-architecture-baseline/quickstart.md` and confirm no unresolved placeholder remains in the feature artifacts (FR-011)
- [x] T021 Complete the requirement-to-evidence audit in `specs/006-clean-architecture-baseline/tasks.md` and mark only tasks with passing evidence complete (FR-001-FR-011)

## Dependencies and Execution Order

```text
Artifact readiness
  -> US1 marker baseline
  -> US2 placement guidance
  -> US3 no-business-code audit
  -> full build and final evidence
```

- T004 through T011 can run in parallel after T001-T003.
- T014 can run in parallel with T013 because it updates a separate feature contract.
- T015 follows T013 so the README points to the finalized guide.
- T017 and T018 follow marker and documentation work.
- T019-T021 run only after every selected story checkpoint passes.

## Implementation Strategy

1. Pin the active approved feature and capture the pre-change source allowlist.
2. Add the eight empty query markers as the minimal source-tree delta.
3. Turn the living architecture guide into the practical placement reference.
4. Prove no business source, Product endpoint, dependency, contract, route, or migration appeared.
5. Run the full Maven reactor and record evidence.

## Evidence Record

| Task(s) | Command/environment | Result | Evidence summary | Date/owner |
|---------|---------------------|--------|------------------|------------|
| T003 | `Get-ChildItem services -Recurse -File -Filter *.java` plus exact path comparison | PASS | Exactly 18 approved Java files before the marker change | 2026-07-15 / Codex |
| T012 | PowerShell eight-path `Test-Path`/length audit and gateway negative check | PASS | 8 present, 0 missing, 0 non-empty; gateway query marker absent | 2026-07-15 / Codex |
| T016 | `rg --line-number` over the guide, placement contract, and README | PASS | Dependency direction, all placement concerns, create-on-demand, anti-patterns, and README link found | 2026-07-15 / Codex |
| T017-T018 | Exact Java path comparison plus prohibited Java/YAML/POM/migration/contract `rg` scans | PASS | Exactly 18 Java files; no controller/use case/entity/Product route/business changeset/new feature stack dependency or Product HTTP contract | 2026-07-15 / Codex |
| T019 | `.\mvnw.cmd clean verify` using Java 21.0.10 | PASS | Parent plus all 9 services succeeded; 9 context tests passed; total time 01:10 | 2026-07-15 / Codex |
| T020-T021 | Quickstart static checks, compiled-class allowlist, placeholder scan, task-format check, and coverage audit below | PASS | Exactly 9 production classes compiled, 0 unresolved template placeholders, and all 21 tasks use the required format | 2026-07-15 / Codex |

## Requirement-to-Evidence Audit

| Requirement set | Passing evidence | Result |
|-----------------|------------------|--------|
| FR-001-FR-003 | T004-T012: eight business-service query markers exist and are empty; gateway query marker remains absent | PASS |
| FR-004-FR-007 | T013-T016: guide, reviewer contract, and README cover every required concern, inward dependencies, create-on-demand, and prohibited patterns | PASS |
| FR-008-FR-010 | T017-T018: exact 18-file Java allowlist and negative scans for behavior, Product routes/contracts, migrations, and unapproved runtime dependencies | PASS |
| FR-011 | T019-T020: full Maven reactor succeeds, exactly nine production classes compile, quickstart passes, and governing artifacts contain no unresolved template placeholder | PASS |
