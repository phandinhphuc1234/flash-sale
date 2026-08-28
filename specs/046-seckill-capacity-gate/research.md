# Research: Adaptive Seckill Capacity Gate

## Decision 1 — Use an open arrival-rate model per stage

**Decision**: Invoke k6 `constant-arrival-rate` once per stage from PowerShell.

**Rationale**: The existing ladder uses per-VU iterations, which measures a burst/concurrency stage,
not a fixed requests-per-second rate. A stage-per-process design lets the orchestrator make a safe
continue/stop decision from completed evidence and keeps a hard wall-clock boundary.

**Alternatives considered**:

- One static ramping scenario: rejected because it cannot safely stop after a previous stage breach.
- Only the existing per-VU ladder: retained for burst evidence, but insufficient for an RPS ceiling.

## Decision 2 — Guardrails are configurable and topology-specific

**Decision**: Default p95/p99/error thresholds are 300 ms/700 ms/1%, with two consecutive stage
breaches; correctness and dropped-iteration signals stop immediately.

**Rationale**: This is a conservative internship demonstration target for the current small EKS
topology, not a production SLA. k6 thresholds are suitable for expressing pass/fail criteria, while
the report must preserve raw metrics.

**Alternatives considered**:

- A universal 500 RPS promise: rejected because hardware, route, duration, allocation, and network
  conditions materially change the result.
- Stop on one slow sample: rejected as too sensitive to transient noise.

## Decision 3 — Local-first, explicit cloud execution

**Decision**: Validation-only is the default; `-Run` is required for load. Cloud use is selected by
the operator's base URL and must begin with a low cap.

**Rationale**: Prevents accidental public traffic and makes the test repeatable in Compose before
touching EKS. It also respects the repository's secret and mutation boundaries.

## Decision 4 — Correctness has priority over throughput

**Decision**: Every stage tracks winner/replay/sold-out/pending/unexpected outcomes and aborts on
oversell or identity mismatch.

**Rationale**: Redis Lua atomic admission, PostgreSQL durable truth, and Kafka outbox convergence are
the project's differentiators; a raw RPS number without those invariants is misleading.
