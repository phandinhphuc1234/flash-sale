# Implementation Plan: Adaptive Seckill Capacity Gate

**Branch**: `codex/phase26-seckill-capacity` | **Date**: 2026-08-28 | **Spec**: [spec.md](spec.md)

## Summary

Add a safe, parameterized capacity runner around the existing Flash Sale reservation contract. A
PowerShell orchestrator starts with a warm-up, invokes a k6 `constant-arrival-rate` stage, parses a
sanitized summary, waits for cooldown, and either increases the rate or stops. The first breach and
last good stage are preserved. No application Java code, contracts, schemas, or deployment manifests
change.

## Technical Context

**Language/Version**: PowerShell 7, k6 JavaScript

**Primary Dependencies**: Existing k6 CLI and PowerShell `System.Diagnostics.Process`. The runner
does not require kubectl, Prometheus, Docker, or a database connection.

**Storage**: Ignored JSON result files under `load-tests/flashsale-service/results`; no durable schema change

**Testing**: PowerShell parser/static tests, k6 script syntax, local disposable Compose run, optional bounded cloud run

**Target Platform**: Windows operator workstation driving local Compose or AWS EKS through api-gateway

**Project Type**: Repository-level operational/load-test tooling

**Performance Goals**: Configurable adaptive stages; default hard cap 500 RPS, default p95 300 ms and p99 700 ms guardrails

**Constraints**: Explicit `-Run`, bounded child processes, no secrets in output, no cross-service database access, no mutation in validation mode

**Scale/Scope**: One campaign/variant fixture, one operator, one load generator, 25–500 RPS default stage range

## Constitution Check

- Requirements are traceable to spec FR/SC and user-approved scope.
- No service boundary, database ownership, API/Kafka contract, or production dependency changes.
- Existing Gateway/HTTP contracts and Redis/PostgreSQL/Kafka invariants are observed, not reimplemented.
- Shared assets remain under `infra/` and `load-tests/`.
- Existing Actuator/Prometheus data is consumed without adding Java registry code.
- Parser/static, k6, load, and cleanup gates are defined; full Maven build is not required because no Java source changes occur.

## Design

### Runner flow

1. Validate parameters and parse the ignored token file; user creation remains an explicit fixture
   preparation step outside the capacity runner. Reserve one extra second of token headroom per
   constant-arrival-rate stage so a boundary iteration cannot reuse the next stage's identity.
2. Prepare the disposable cloud fixture through the existing Gateway and Inventory-owned Job. The
   fixture-only path separates physical/campaign allocation from reservation quantity and extends
   the active window without changing a service contract or business rule.
3. Run warm-up at a low rate and discard it from capacity conclusions.
4. For each stage, invoke k6 with `constant-arrival-rate`, unique idempotency keys, replay checks,
   and a bounded graceful-stop window so an in-flight winner can complete its replay.
5. Parse the stage summary. Platform health and Kafka lag are recorded by the existing Phase 25
   dashboard and verification gates rather than making the load generator depend on a second endpoint.
6. Apply immediate danger checks, then consecutive latency/error checks.
7. On a green stage, cooldown and increase rate; on breach, stop, write the final report, and clean temporary files.

### Guardrails

| Signal | Default | Action |
|---|---:|---|
| p95 | 300 ms | breach; stop after 2 consecutive stages |
| p99 | 700 ms | breach; stop after 2 consecutive stages |
| HTTP error rate | 1% | breach; stop after 2 consecutive stages |
| unexpected errors | 0 | immediate stop |
| dropped iterations | 0 | immediate stop |
| oversell/identity mismatch | 0 | immediate stop |
| hard cap | 500 RPS | stop with `at_or_above_max_tested` if all stages pass |

### Files

```text
load-tests/flashsale-service/adaptive-arrival-rate.js
infra/scripts/gitops/phase26-seckill-capacity.ps1
infra/scripts/gitops/tests/phase26-seckill-capacity.tests.ps1
infra/scripts/gitops/phase22-internal-e2e.ps1
infra/scripts/gitops/phase22-internal-e2e-memory.ps1
specs/046-seckill-capacity-gate/{spec,plan,research,data-model,quickstart,validation,tasks}.md
specs/046-seckill-capacity-gate/contracts/capacity-runner.md
```

### Complexity Tracking

| Decision | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Invoke one bounded k6 process per stage | Allows the PowerShell runner to decide whether to continue | A single static k6 scenario cannot stop based on prior stage evidence |
| Pre-created ignored token file | Keeps identity setup and cleanup outside the capacity probe | Creating thousands of users inside the runner would couple load generation to Auth and leak fixture state |
| Reuse the Phase 22 fixture boundary in fixture-only mode | Preserves Gateway and Inventory service ownership while allowing a larger disposable allocation | Direct SQL, cross-service database access, or one giant smoke purchase would invalidate the evidence |

## Verification Strategy

- Static parser test rejects unbounded processes, secret output, database CLI, Kubernetes mutation, and missing guardrails.
- k6 script is inspected with `k6 inspect` when available and executed only through the bounded runner.
- Local run uses a disposable fixture and records a result under the ignored results directory.
- Cloud run is explicit, starts with a low cap, and must be preceded by a healthy Argo/Phase 25 check.
- Evidence records command, parameters, stage summaries, stop reason, and cleanup result without tokens.
