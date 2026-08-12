# Specification Quality Checklist: Gateway Redis Rate Limiter

**Purpose**: Validate scope, behavior, distributed correctness, security and compatibility before planning
**Created**: 2026-07-22
**Feature**: [spec.md](../spec.md)
**Plan**: [plan.md](../plan.md) — Approved after clean pre-approval analysis
**Risk level**: High — public ingress, quota, identity, concurrent Redis state and failure semantics

> This checklist evaluated the specification before approval. It is not implementation evidence and
> does not approve production code by itself.

## Content Quality

- [x] CHK001 The problem, goal, in-scope and out-of-scope behavior are explicit.
- [x] CHK002 User stories are prioritized and independently demonstrable.
- [x] CHK003 Functional requirements, invariants and measurable outcomes use stable identifiers.
- [x] CHK004 Baseline behavior and the Feature 013 delta are distinguishable.
- [x] CHK005 Implementation details are limited to user-approved or constitutional constraints; HOW remains for planning.

## Requirement Decisions

- [x] CHK006 Route, method, quota, refill, request cost, timeout and fail-open policy are exact.
- [x] CHK007 Missing or invalid direct-client-IP behavior is resolved and encoded in requirements.
- [x] CHK008 Bucket TTL refresh and material policy-change state lifecycle are resolved.
- [x] CHK009 Opaque HMAC encoding and minimum enabled-secret strength are resolved.
- [x] CHK010 No other blocking `[NEEDS CLARIFICATION: ...]` marker remains.
- [x] CHK011 Retry, ambiguous execution, missing state, corrupt state and coordinator-loss outcomes are explicit.
- [x] CHK012 Production throughput and quota claims are not invented from the local/demo preset.

## Distributed Correctness and Security

- [x] CHK013 Same-bucket atomicity and the deterministic concurrency outcome are measurable.
- [x] CHK014 Successful quota exhaustion is distinguished from infrastructure uncertainty.
- [x] CHK015 Redis state is explicitly ephemeral and every invalid-state path has bounded cleanup.
- [x] CHK016 Downstream 429 and all normally obtained downstream responses remain downstream-owned.
- [x] CHK017 Raw identity, secrets and high-cardinality telemetry dimensions are prohibited.
- [x] CHK018 Forwarding headers remain untrusted until a later Kubernetes ingress decision.

## Contracts, Ownership and Compatibility

- [x] CHK019 The additive Feature 012 header delta and exact unchanged body are identified.
- [x] CHK020 One Redis runtime and root/service ownership boundaries are explicit.
- [x] CHK021 ADR 0003 remains governing and the need for proposed ADR 0004 is identified.
- [x] CHK022 Kafka, database migration, business idempotency and OpenTelemetry runtime omissions are justified.

## Verification Readiness

- [x] CHK023 Unit, real-Redis integration, HTTP contract, failure, concurrency, load measurement and module regression layers are identified.
- [x] CHK024 Each critical invariant and client/operator outcome has a candidate acceptance scenario.
- [x] CHK025 The specification has no blocking open decision and is ready for implementation approval.

## Findings

| Item | Severity | Finding | Required artifact change | Owner | Status |
|------|----------|---------|--------------------------|-------|--------|
| CHK007 | RESOLVED | Missing direct socket IP bypasses acquisition without a shared fallback bucket. | Encoded in FR-014 and US3 scenario 6. | Security/Gateway owner | RESOLVED 2026-07-22 |
| CHK008 | RESOLVED | Rejected requests do not refresh TTL; material policy changes use a new state version. | Encoded in FR-020 and US1 scenario 5. | Gateway/Platform owner | RESOLVED 2026-07-22 |
| CHK009 | RESOLVED | HMAC output and enabled-secret validation use the approved Option A. | Encoded in FR-021 and US2 scenario 6. | Security/Gateway owner | RESOLVED 2026-07-22 |
| CHK010/011/015/025 | RESOLVED | A wrong-type or `PTTL = -1` key needs bounded cleanup without a silent quota reset. | P0 Option A is encoded in FR-019/020/023: attach only the normal TTL once, preserve type/fields, fail open, and do not refresh later still-invalid failures; an otherwise-valid hash may resume normal behavior because no marker is stored. Existing finite TTL only decreases. | Gateway/Platform owner | RESOLVED 2026-07-22 |

## Notes

- Checklist status is 25/25 passing after the P0 TTL recovery clarification.
- This checklist validates specification quality only; artifact approval and implementation evidence
  remain separate gates.
