# Distributed Edge Design Checklist: Gateway Redis Rate Limiter

**Purpose**: Formal reviewer gate for design completeness, distributed correctness, security, HTTP compatibility, rollout, and evidence planning  
**Created**: 2026-07-22  
**Feature**: [spec.md](../spec.md)  
**Plan**: [plan.md](../plan.md)  
**Risk level**: High — public ingress, identity/HMAC, concurrent Redis state, fail-open, and client contract

> This checklist evaluates the quality and completeness of requirements and design artifacts. It is
> not implementation evidence or artifact approval.

## Requirement Completeness and Clarity

- [x] CHK001 Are the protected route, exact method, quota, refill, cost, timeout, and default activation state specified without relying on examples? [Completeness, Spec §FR-001–003, FR-015–016]
- [x] CHK002 Are missing identity, successful exhaustion, coordinator failure, unexpected failure, and downstream-owned responses distinguished by exact terminal outcomes? [Coverage, Spec §FR-006, FR-011, FR-014, FR-017–019]
- [x] CHK003 Are TTL calculation, allowed/rejected refresh behavior, missing/corrupt state, and material policy-change lifecycle objectively defined? [Clarity, Spec §FR-019–020]
- [x] CHK004 Are all security-sensitive HMAC encoding, input separation, secret source/minimum, failure, rotation, and exposure rules exact? [Security, Spec §FR-013, FR-021]
- [x] CHK005 Are performance statements limited to approved timeout/correctness measurements without presenting the local quota as a production SLO? [Consistency, Spec §FR-003, NFR-001, NFR-006]

## Distributed Correctness and State

- [x] CHK006 Does the design define one atomic same-key boundary, shared coordinator clock, exact integer representation, and no negative/overspent state? [Correctness, Plan §Data, Transactions, and Concurrency; Data Model §3–4]
- [x] CHK007 Does the script contract specify every key/argument/result, validation branch, mutation branch, and O(1) bound? [Completeness, Data Model §4]
- [x] CHK008 Are ambiguous timeout/connection outcomes explicitly non-retryable while framework NOSCRIPT cache recovery remains distinguishable? [Clarity, Research §Decision 2; Plan §Failure, Retry, and Compensation]
- [x] CHK009 Are key loss, expiry, eviction, restart, secret rotation, and state-version changes given bounded recovery consequences without durable-business claims? [Recovery, Data Model §7; ADR 0004 §Consequences]
- [x] CHK010 Are no-expiry, partial/non-numeric, future timestamp, and invalid result cases classified consistently as typed fail-open rather than silent repair? [Consistency, Spec §FR-019; Data Model §4–6]

## Architecture and Ownership

- [x] CHK011 Does each rate-limit concern have one concrete lean Gateway package and avoid an invented business `domain/application/adapter` hierarchy? [Architecture, Plan §Architecture and Lean Gateway Mapping; ADR 0003]
- [x] CHK012 Is Redis driver code isolated behind a technical coordinator seam so the filter and HTTP writer do not depend on Redis types? [Dependency Direction, Plan §Architecture and Lean Gateway Mapping]
- [x] CHK013 Are Product data/business rules, service databases, Kafka, and durable idempotency explicitly outside the Gateway capability? [Ownership, Spec §Out of scope; Plan §Context and Service Ownership]
- [x] CHK014 Are shared Compose/Redis assets root-owned while dependencies, policy configuration, Lua, and tests remain service-owned? [Constitution, Plan §Context and Service Ownership; Configuration Contract §4]
- [x] CHK015 Is the architectural change recorded with context, decision, alternatives, consequences, migration, rollback, and Kubernetes boundary? [Governance, ADR 0004]

## HTTP, Security, and Compatibility

- [x] CHK016 Is the Gateway-owned 429 exact in body, content type, retry/cache headers, and forbidden accounting headers? [Contract, HTTP Contract §2]
- [x] CHK017 Are allowed, identity-unavailable, fail-open, writer-fallback, committed, and downstream-429 header/ownership rules complete? [Scenario Coverage, HTTP Contract §4–6]
- [x] CHK018 Does the design prevent a serialization fallback 500 from retaining rate-limit headers and keep one central error writer? [Compatibility, Research §Decision 7; HTTP Contract §6]
- [x] CHK019 Are direct-address normalization, forwarding-header distrust, length-prefixed HMAC input, and plaintext/digest exposure boundaries unambiguous? [Security, Data Model §2; Plan §Security and Abuse Controls]
- [x] CHK020 Are secret entropy responsibility, runtime Base64/length validation, replica consistency, and coordinated rotation accurately separated? [Security/Operations, Configuration Contract §2, §6]

## Operability, Rollout, and Verification

- [x] CHK021 Are Redis readiness exclusion and fail-open availability consistent rather than implementing HTTP fail-open but deployment fail-closed? [Operability, Research §Decision 10; Plan §Observability and Operations]
- [x] CHK022 Are metrics, Observation, logs, correlation, Prometheus, and deferred OpenTelemetry/alerts defined with bounded cardinality and no false tracing claim? [Observability, Plan §Observability and Operations; Research §Decision 9]
- [x] CHK023 Does rollback use the default-off feature flag and natural expiry without destructive Redis scans/flushes? [Recovery, Plan §Migration, Rollout, and Rollback; ADR 0004 §Rollback]
- [x] CHK024 Are unit, real-Redis integration, contract, failure, concurrency, load measurement, Compose, module, and full-build layers mapped to commands/evidence, with N/A layers justified? [Verifiability, Plan §Verification Strategy and Evidence; Quickstart §4–10]
- [x] CHK025 Is the future Kubernetes work explicitly separated into trusted ingress, secret rotation, Redis HA/security/resources, replica rollout, monitoring/tracing, and manifest validation decisions? [Scope Boundary, Quickstart §12; ADR 0004 §Future Kubernetes decision boundary]

## Findings

| Item | Severity | Finding | Required artifact change | Owner | Status |
|------|----------|---------|--------------------------|-------|--------|
| CHK003/007/009/023 | RESOLVED | Persistent/wrong-type corrupt-state cleanup needed one bounded mutation and recovery/rollback rule. | P0 Option A is synchronized as expiry-only normalization with no repair/reset/delete/marker; still-invalid calls do not refresh, otherwise-valid hashes may resume, existing finite TTL only decreases, and seeded-state tests cover both paths. | Gateway/Platform owner | RESOLVED 2026-07-22 |

## Notes

- Checklist status: 25/25 design-quality items passing after the P0 clarification.
- The reviewer audience is Gateway/security/observability/HTTP-contract owners; this is a formal
  pre-task/pre-implementation gate, not a lightweight runtime checklist.
- Test results and command evidence belong in a later `validation.md`, not in this checklist.
