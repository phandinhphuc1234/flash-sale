# Feature Specification: Adaptive Seckill Capacity Gate

**Feature Branch**: `codex/phase26-seckill-capacity`
**Created**: 2026-08-28
**Status**: Approved
**Input**: User request: test seckill near the safe capacity, stop before harming the cluster, and preserve measurable evidence.

## Problem and Scope

The current seckill load ladder reports fixed concurrency stages but does not find a safe upper
capacity or stop automatically when latency, errors, or platform health deteriorate. Operators need
a bounded, repeatable capacity test that separates a last known-good stage from the first unsafe
stage without claiming a production SLA.

### In Scope

- Warm-up and rate-based measurement stages with a configurable hard cap.
- Automatic stop on repeated performance/correctness breaches or immediate platform danger signs.
- Separate evidence for last-good and first-breach stages, including business invariants.
- Redacted result files and cleanup of temporary token material.
- Local-first operation with an explicit opt-in for live execution.

### Out of Scope

- Changing application business logic, stock semantics, Kafka contracts, schemas, or database migrations.
- Horizontal autoscaling, node resizing, managed load-generator infrastructure, or production traffic.
- Slack, external alert delivery, or a universal production capacity/SLA claim.

## User Scenarios & Testing

### User Story 1 - Find a safe seckill ceiling (Priority: P1)

As an operator, I want the runner to increase load gradually and stop before the system becomes
unhealthy, so that I can report the highest verified safe rate.

**Why this priority**: A bounded capacity result is safer and more useful than an unbounded stress run.

**Independent Test**: Run the runner against a disposable fixture with a low cap and verify that it
records every stage and stops at the configured guardrail or hard cap.

**Acceptance Scenarios**:

1. **Given** a valid fixture and a configured start/step/cap, **when** each stage completes, **then**
   the runner records rate, duration, requests, latency, errors, and business outcomes.
2. **Given** two consecutive stage breaches, **when** the next decision is evaluated, **then** the
   runner stops and reports the last-good rate and first-breach rate.
3. **Given** a hard danger signal such as an unexpected error, dropped iteration, oversell, or
   unhealthy workload, **when** it is observed, **then** the runner stops immediately.

### User Story 2 - Prove seckill correctness under contention (Priority: P1)

As a reviewer, I want capacity results to include correctness invariants, so that throughput cannot
hide overselling or duplicate durable effects.

**Independent Test**: Run a stage with unique idempotency keys and verify winner counts, replay IDs,
Redis allocation, PostgreSQL durable rows, and Kafka convergence in the supplied evidence.

**Acceptance Scenarios**:

1. **Given** finite allocation, **when** concurrent reservations contend, **then** winners never
   exceed allocation and excess attempts are classified as sold-out or acceptance-pending.
2. **Given** a winner replay, **when** the same idempotency key is submitted, **then** the original
   reservation and purchase-request identities are preserved.
3. **Given** event processing completes, **when** the stage settles, **then** outbox/Kafka lag and
   durable counts are recorded without querying another service database.

### User Story 3 - Leave an auditable and recoverable test result (Priority: P2)

As an operator, I want sanitized artifacts and cleanup after every run, so that credentials and test
fixtures do not remain in the repository or cluster.

**Independent Test**: Run validation-only mode and a disposable local run, then inspect the result
JSON and working tree for absence of tokens, passwords, and untracked result files.

**Acceptance Scenarios**:

1. **Given** validation-only mode, **when** the runner starts, **then** no k6 process, Kubernetes
   mutation, database write, or Secret read occurs.
2. **Given** a run exits normally or by guardrail, **when** cleanup executes, **then** temporary token
   files are removed and the final report contains only sanitized metadata.

### Edge Cases

- A stage reaches the hard cap without a breach; the report must say `at_or_above_max_tested`, not
  claim the true system capacity.
- The load generator cannot sustain the requested arrival rate; dropped iterations are an immediate
  stop condition.
- A stage is affected by expected sold-out responses; these are not transport errors.
- Prometheus or optional resource telemetry is unavailable; the test must not fabricate values and
  must retain application-level evidence.
- The process exceeds its wall-clock budget; it must terminate child processes and emit a bounded
  diagnostic.

## Requirements

### Functional Requirements

- **FR-001**: The runner MUST default to validation-only and require an explicit run switch for load generation.
- **FR-002**: The runner MUST execute a warm-up followed by increasing arrival-rate stages with configurable start, step, duration, cooldown, and hard cap.
- **FR-003**: The runner MUST use a bounded wall-clock deadline and terminate its child k6 process on exit.
- **FR-004**: The runner MUST stop after configurable consecutive breaches of latency or error thresholds and immediately on correctness or load-generator danger signals such as unexpected responses or dropped iterations; platform health is a pre-run Phase 25 gate.
- **FR-005**: The runner MUST record stage metrics, business outcomes, stop reason, last-good rate, and first-breach rate in a sanitized JSON report.
- **FR-006**: The load profile MUST preserve the existing reservation idempotency and winner/replay checks.
- **FR-007**: The runner MUST NOT query another service's database or print passwords, Secret values, JWTs, tokens, or Checkout data.
- **FR-008**: Temporary token and result files MUST remain ignored by Git and be removed where possible after the run.
- **FR-009**: The runner MUST support local-first execution and make cloud execution an explicit operator choice through the base URL and run switch.

### Non-Functional Requirements

- **NFR-PERF-001**: Default guardrails are p95 < 300 ms, p99 < 700 ms, HTTP error rate < 1%, and zero unexpected errors; all are configurable.
- **NFR-REL-001**: A single transient breach MUST NOT stop the test; the default is two consecutive breached stages, while danger signals stop immediately.
- **NFR-SAFE-001**: The default hard cap MUST be 500 requests/second and the default stage duration MUST be 30 seconds.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Every run produces a report containing all completed stages and a truthful `last_good_rate` or `at_or_above_max_tested` outcome.
- **SC-002**: A correctness breach causes a non-zero exit and no later stage is started.
- **SC-003**: A valid run records zero unexpected errors, no oversell, and 100% same-key replay identity preservation.
- **SC-004**: A stage meeting the configured latency/error guardrails records p95/p99 and request-rate evidence; thresholds are visible in the report.
- **SC-005**: Validation-only mode leaves Kubernetes, database, Kafka, Redis, ECR, and Secret state unchanged.

## Assumptions

- The existing Flash Sale reservation HTTP contract, `load-tests/flashsale-service/reservation.js`,
  and Grafana/Prometheus deployment remain the source of application metrics.
- Capacity tests use a disposable campaign/variant allocation sized above the planned request count.
- A result is topology-specific evidence for the current three-node development EKS cluster, not a
  production promise.
- Resource telemetry is best-effort; application correctness and HTTP metrics remain mandatory.

## Constitutional Constraints

- **Service ownership**: No service database is queried; the runner uses public Gateway/HTTP contracts and existing fixture boundaries.
- **External ingress**: Cloud runs use api-gateway; direct-service mode is retained only for local comparison.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: The test verifies Redis Lua atomic admission and PostgreSQL durable truth without changing either implementation.
- **Messaging reliability**: Existing idempotency, outbox, replay, and lag evidence are measured; no consumer behavior changes.
- **Root infrastructure ownership**: Runner and load assets live under `infra/` and `load-tests/`; no service module is changed.
- **Observability**: Existing Actuator/Prometheus signals are consumed; no manual registry or Java metric is added.
- **Verification**: PowerShell parser/static safety tests, k6 contract checks, local run, and bounded cloud run are required where applicable.
- **Architecture decisions**: No new service boundary, dependency, or deployment coupling; no ADR required.

## Approval and History

- 2026-08-28 — User approved implementation of an adaptive near-capacity seckill test.
