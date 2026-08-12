# Requirements Quality Checklist: Gateway-Owned Error Handling

**Purpose**: Evaluate whether Feature 011 is complete and decision-ready before planning
**Created**: 2026-07-22
**Feature**: [spec.md](../spec.md)
**Risk level**: High/draft — public ingress, security responses, availability classification,
trace correlation, and downstream compatibility

> This checklist evaluates the specification, not implementation evidence. Open decision items stay
> unchecked until the Gateway/Platform owner answers them and the decisions are encoded in `spec.md`.

## Requirement Decisions

- [x] CHK001 The problem, goal, in-scope behavior, exclusions, and baseline delta are explicit. [Completeness]
- [x] CHK002 Gateway-owned failures and downstream-owned HTTP responses are separated explicitly. [Ownership]
- [x] CHK003 User stories are prioritized and independently testable. [Testability]
- [x] CHK004 The first-slice failure taxonomy and HTTP status policy are approved in FR-007. [Availability, Blocking]
- [x] CHK005 Missing or invalid trace-ID behavior is approved in FR-008. [Observability, Blocking]
- [x] CHK006 Unknown-route disclosure behavior is approved in FR-009. [Security, Blocking]
- [x] CHK007 No blocking `[NEEDS CLARIFICATION: ...]` marker or OPEN Human Decision remains before approval. [Clarity, Blocking]
- [x] CHK008 Success criteria are measurable and do not prescribe an implementation structure. [Measurability]

## Security and Failure Safety

- [x] CHK009 Existing 400, 401, and 403 Product Admin outcomes are protected as compatibility constraints. [Compatibility]
- [x] CHK010 Client-safe messages prohibit raw exception, token, provider, signing, stack, and internal-address details. [Security]
- [x] CHK011 Authentication challenge semantics are required for 401 responses. [HTTP Security]
- [x] CHK012 Already-committed responses and single-response behavior are covered. [Reactive Safety]
- [x] CHK013 Retry, timeout duration, rate limit, circuit breaker, and fallback policies are not inferred. [Constraint]

## Architecture and Ownership

- [x] CHK014 The Gateway owns only edge classification and rendering; service business failures remain service-owned. [Boundary]
- [x] CHK015 Downstream 4xx and 5xx pass-through is a mandatory invariant, including downstream 500. [Compatibility]
- [x] CHK016 Dependencies on service-specific DTOs, enums, exceptions, JPA models, and business libraries are prohibited. [Loose Coupling]
- [x] CHK017 The lean Gateway package profile and its governing ADR remain applicable. [Architecture]
- [x] CHK018 Controller advice, persistence, Kafka, Redis, and service-domain behavior are outside this feature. [Scope]

## Contract and Verification Readiness

- [x] CHK019 The retained gateway envelope `{code,message,traceId}` is explicit. [Contract]
- [x] CHK020 Every first-slice failure category has an unambiguous status, code, safe message, and trace outcome. [Contract, Blocking]
- [x] CHK021 Representative downstream 400, 404, 409, and 500 pass-through tests are required. [Coverage]
- [x] CHK022 Security, request-boundary, infrastructure, unexpected-error, committed-response, and leakage cases are identified. [Coverage]
- [x] CHK023 All functional requirements have acceptance criteria precise enough for dependency-ordered task generation. [Readiness, Blocking]

## Findings

| Item | Severity | Finding | Required artifact change | Owner | Status |
|------|----------|---------|--------------------------|-------|--------|
| CHK004/CHK020 | HIGH | The initial Gateway failure taxonomy and status mapping required owner approval. | FR-007 and `contracts/gateway-error-http.md` now encode every approved code/status/message. | Gateway/Platform + security owners | RESOLVED 2026-07-22 |
| CHK005 | HIGH | A Gateway-owned error required a defined missing/invalid trace outcome. | FR-008 now preserves a valid caller value or generates one error correlation ID without bypassing route validation. | Gateway/Platform + observability owners | RESOLVED 2026-07-22 |
| CHK006 | HIGH | Unknown-path behavior required an explicit route-disclosure decision. | FR-009 now preserves deny-by-default 401/403 behavior and uses generic `ACCESS_DENIED` for authenticated unknown paths. | Gateway/Platform + security owners | RESOLVED 2026-07-22 |

## Notes

- No blocking requirement item remains after the Gateway/Platform owner approved Q1/Q2/Q3 Option A.
- The specification deliberately does not standardize downstream service error bodies.
- Adding fields such as `timestamp`, `path`, or service-specific metadata is not required for the
  stated goal and would be a separate compatibility decision.
