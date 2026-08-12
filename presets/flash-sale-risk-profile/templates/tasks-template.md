---
description: "Risk-based, traceable task list for a Flash Sale feature"
---

# Tasks: [FEATURE NAME]

**Input**: Approved artifacts from `/specs/[###-feature-name]/`
**Prerequisites**: `spec.md` and `plan.md` are required; use `research.md`, `data-model.md`,
`contracts/`, `quickstart.md`, and checklists when generated.
**Approval gate**: No blocking clarification or failed Constitution Check may remain.

## Format: `[ID] [P?] [Story] Description (trace references)`

- **[P]**: Safe to run in parallel because files and dependencies do not overlap.
- **[Story]**: Owning user story, such as `[US1]`; omit only for setup/cross-cutting work.
- **Trace references**: Add applicable `(FR-..., NFR-..., INV-..., AC-..., RISK-...)` identifiers.
- Every task names exact file paths, completion evidence, and dependencies when ordering is not obvious.

## Test and Validation Ordering

> When the approved specification or plan selects test-first for a risk or behavior, create the mapped
> test first and record its expected failure before implementation. Otherwise follow the approved
> risk-based ordering. Required tests and validation evidence are never optional.

<!--
  The tasks below are samples. /speckit-tasks MUST replace them with concrete, dependency-ordered
  tasks derived from the approved artifacts. Do not keep unused samples or invent requirements.
-->

## Phase 1: Setup and Artifact Readiness

**Purpose**: Confirm scope, traceability, and build boundaries before production changes.

- [ ] T001 Confirm active feature path, approval status, and absence of blocking clarifications in `specs/[###-feature]/spec.md`
- [ ] T002 Build a requirement-to-story-to-evidence map in `specs/[###-feature]/plan.md` (FR/INV/AC)
- [ ] T003 [P] Confirm affected module paths and commands from `specs/[###-feature]/plan.md`
- [ ] T004 [P] Update required ADR or contract design artifacts at [exact path] before code changes

**Checkpoint**: Approved scope and executable validation strategy are explicit.

---

## Phase 2: Foundational Risk Controls

**Purpose**: Implement only prerequisites that block every selected story.

- [ ] T005 Add backward-compatible service-owned migration at `services/<service>/src/main/resources/db/migration/[file]` (FR/INV refs)
- [ ] T006 [P] Add/update versioned HTTP or Kafka contract at [exact contract path] (FR/AC refs)
- [ ] T007 Implement idempotency key scope, storage, lifetime, and replay outcome at [exact paths] (INV/RISK refs)
- [ ] T008 Implement transactional outbox/inbox and recovery behavior at [exact paths] (INV/RISK refs)
- [ ] T009 Implement planned concurrency control or Redis Lua operation at [exact paths] (INV/RISK refs)
- [ ] T010 [P] Configure declarative liveness, readiness, and Prometheus exposure at `services/<service>/src/main/resources/application.yml`
- [ ] T011 [P] Implement trace-ID propagation at [exact HTTP/Kafka paths] (NFR refs)
- [ ] T012 [P] Place shared platform assets under `infra/[exact path]`; keep runtime configuration and migrations with the owning service

Delete any item that is genuinely not applicable and retain the plan's rationale; do not create empty
infrastructure abstractions merely to satisfy this sample.

**Checkpoint**: All story-blocking controls and contracts are ready.

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Business outcome]
**Independent Test**: [Approved independent scenario]
**Trace set**: [FR/INV/AC identifiers]

### Risk-mapped tests for User Story 1

- [ ] T013 [P] [US1] Add domain invariant unit test at `services/<service>/src/test/java/[path]` (INV-...)
- [ ] T014 [P] [US1] Add HTTP/Kafka contract test at `services/<service>/src/test/java/[path]` (FR-.../AC-...)
- [ ] T015 [P] [US1] Add database/cache integration test with real boundary at `services/<service>/src/test/java/[path]` (RISK-...)
- [ ] T016 [P] [US1] Add duplicate/concurrency/failure-path test at `services/<service>/src/test/java/[path]` (INV-.../AC-...)

### Implementation for User Story 1

- [ ] T017 [P] [US1] Implement domain concept/invariant at `services/<service>/src/main/java/[domain-path]` (INV-...)
- [ ] T018 [US1] Implement application use case and ports at `services/<service>/src/main/java/[application-path]` (FR-...)
- [ ] T019 [P] [US1] Implement outbound adapter at `services/<service>/src/main/java/[adapter-path]` (FR-...)
- [ ] T020 [US1] Implement inbound HTTP/Kafka adapter at `services/<service>/src/main/java/[adapter-path]` (AC-...)
- [ ] T021 [US1] Add metrics, structured logging, and trace correlation at [exact paths] (NFR-...)
- [ ] T022 [US1] Run [exact US1 validation commands] and record result/evidence in `specs/[###-feature]/tasks.md`

**Checkpoint**: User Story 1 is independently demonstrable and its mapped evidence passes.

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Business outcome]
**Independent Test**: [Approved independent scenario]
**Trace set**: [FR/INV/AC identifiers]

### Risk-mapped tests for User Story 2

- [ ] T023 [P] [US2] Add [test type] at `services/<service>/src/test/java/[path]` (trace refs)
- [ ] T024 [P] [US2] Add failure/compatibility test at `services/<service>/src/test/java/[path]` (trace refs)

### Implementation for User Story 2

- [ ] T025 [P] [US2] Implement domain/application change at `services/<service>/src/main/java/[path]` (trace refs)
- [ ] T026 [US2] Implement required adapter/contract behavior at `services/<service>/src/main/java/[path]` (trace refs)
- [ ] T027 [US2] Run [exact US2 validation commands] and record result/evidence in `specs/[###-feature]/tasks.md`

**Checkpoint**: User Stories 1 and 2 remain independently testable.

---

[Add one phase per remaining user story, preserving priority, independent test, trace set, risk-mapped
tests, implementation tasks, and checkpoint.]

## Final Phase: Cross-Cutting Verification and Release Readiness

- [ ] TXXX [P] Verify API/event compatibility against every affected contract at [exact paths]
- [ ] TXXX [P] Run security and sensitive-log review for [affected paths]
- [ ] TXXX Run migration forward/rollback or compatibility validation using [exact command]
- [ ] TXXX Run affected module verification: `./mvnw -pl services/<service> -am verify`
- [ ] TXXX Run full verification when required: `./mvnw clean verify`
- [ ] TXXX Run Kubernetes validation when affected: `kubectl apply --dry-run=client -k <overlay>`
- [ ] TXXX Run approved load/failure/concurrency scenario using [exact command] and compare [threshold]
- [ ] TXXX Verify liveness, readiness, Prometheus endpoint exposure, metrics, logs, alerts, and trace propagation
- [ ] TXXX Complete requirement-to-evidence audit and attach CI/report/command evidence to `specs/[###-feature]/tasks.md`
- [ ] TXXX Re-run `/speckit.analyze` and resolve every HIGH/CRITICAL finding before completion

## Dependencies and Execution Order

- Setup and artifact readiness precede production code.
- Foundational controls precede every story that depends on them.
- A story's test ordering follows the approved risk strategy; adapters do not precede required ports/domain behavior without an explicit plan reason.
- Contract producers update contract artifacts before dependent implementations.
- Shared-state tasks are sequential unless the plan proves parallel safety.
- Final verification follows all selected story checkpoints.

## Evidence Record

Record evidence when completing each validation task; a checked box alone is not evidence.

| Task | Command/environment | Result | Evidence link/output | Date/owner |
|------|---------------------|--------|----------------------|------------|
| [T...] | [command] | [PASS/FAIL] | [CI/report/summary] | [date/name] |

## Notes

- Do not change service boundaries without an ADR.
- Do not change API/event behavior without updating the contract file.
- Do not share JPA entities, repositories, schemas, or domain models between services.
- Stop implementation when code reveals an unapproved requirement; update and re-approve artifacts first.
- Commit after one coherent task or task group; do not claim completion while required checks fail.
