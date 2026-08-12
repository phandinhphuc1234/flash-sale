# Implementation Plan: Gateway Rate-Limit Error Contract

**Branch**: `012-gateway-rate-limit-contract` | **Date**: 2026-07-22 | **Spec**: [spec.md](spec.md)
**Input**: Approved feature specification from `specs/012-gateway-rate-limit-contract/spec.md`
**Plan status**: Verified | **Technical owner**: Gateway/Platform owner
**Required reviewers**: Gateway technical owner, observability owner, client-contract reviewer

## Summary

Add the single public code `RATE_LIMIT_EXCEEDED` to the existing Gateway-owned taxonomy and verify
that the existing centralized writer produces HTTP 429 with the unchanged three-field envelope.
Document the additive contract before code and clarify the repository's planned tracing stack:
Micrometer Tracing is the application abstraction, its OpenTelemetry bridge supplies the tracing
implementation, OTLP is the export protocol, and the root-owned OpenTelemetry Collector is the
runtime receiver. This feature adds no limiter and no tracing dependency or runtime configuration.

## Technical Context

**Language/Version**: Java 21

**Framework**: Spring Boot 3.5.16; Spring Cloud Gateway Server WebFlux 4.3.5

**Build**: Root Maven wrapper; independently verified `services/api-gateway`

**Primary dependencies**: Existing Gateway dependencies only. No dependency is added. Future tracing
runtime work is expected to use `micrometer-tracing-bridge-otel` and an OTLP exporter under a separately
approved feature.

**Storage**: N/A; no PostgreSQL, Redis, quota record, key, or TTL

**Communication**: Additive Gateway-owned HTTP error contract; downstream HTTP responses remain pass-through

**Testing**: Unit taxonomy test, writer contract test, existing proxy pass-through tests, module reactor verify

**Target platform**: Existing Gateway runtime; no deployment asset change

**Performance goals**: N/A for this contract-only slice; no request filter is activated

**Constraints**: Preserve all Feature 011 behavior; do not fabricate limiter policy or tracing runtime state

**Scale/scope**: One enum member, focused tests, feature contract, and technology documentation

## Risk Classification

| Dimension | Level | Evidence | Required mitigation/verification |
|-----------|-------|----------|----------------------------------|
| Money/payment | Low | No financial flow | N/A |
| Inventory/concurrency | Low | No stock or limiter state | N/A |
| Security/privacy | Medium | Public abuse-control error and trace field | Fixed safe message; no identity/quota internals; leakage assertions retained |
| Distributed consistency | Low | No distributed state | Downstream response ownership test retained |
| Contract/compatibility | High | Adds a public Gateway error code | Contract-first additive row and exact contract tests |
| Migration/rollback | Low | No data/config migration | Revert additive enum/docs if necessary |
| Load/operability | Medium | Enables a future hot-path control but does not execute it | Explicitly defer quota/load semantics; document tracing ownership accurately |

**Overall risk**: Medium, driven by public-contract compatibility and observability terminology.

**Selected test ordering**: Test-first for the exact new taxonomy/writer contract.

**Approval gates**: User approval in `spec.md`; contract and tasks before production code; module verify before closure.

## Constitution Check

- **Specification traceability**: PASS; FR-001 through FR-008 map to contract, docs, code, and tests.
- **Service ownership**: PASS; the Gateway owns its edge error taxonomy and accesses no service data.
- **Communication**: PASS; the HTTP error contract is documented before implementation and downstream responses remain owned by their services.
- **Data and messaging**: PASS; no database, Redis, Kafka, quota, TTL, or reconciliation behavior.
- **Infrastructure ownership**: PASS; no Collector asset is added. Future shared Collector configuration remains under root `infra/monitoring/`.
- **Observability**: PASS; the plan distinguishes existing correlation behavior from future Micrometer/OpenTelemetry distributed tracing and adds no manual registry/tracer implementation.
- **Dependencies/contracts**: PASS; no dependency change; additive contract is explicit.
- **Validation**: PASS; focused contract tests and the Gateway Maven reactor gate apply. Load, migration, Kafka, database, and Kubernetes validation are omitted because no runtime limiter, data, messaging, or manifest changes.

**Gate result**: PASS before and after design.
**Violations or waivers**: None.

## Context and Service Ownership

| Capability/data | Owning service | Readers/callers | Allowed interaction | Prohibited interaction |
|-----------------|----------------|-----------------|---------------------|------------------------|
| Gateway-owned rate-limit error vocabulary | `api-gateway` | Public API clients and a future Gateway limiter | Centralized Gateway error writer | Importing service business errors or translating downstream 429 bodies |
| Distributed tracing instrumentation standard | Each service for its own runtime config; root `infra/monitoring` for Collector | All services/operators | Micrometer Tracing -> OpenTelemetry bridge/exporter -> OTLP Collector | Direct business/policy dependency on OpenTelemetry SDK or service-local Collector infrastructure |

**Context-map change**: None.

**Boundary decision**: HTTP rejection rendering is a technical edge responsibility. There is no business Aggregate, application use case, persistence port, or service-domain model in this slice.

## Architecture and Boundary Mapping

| Concern | Planned path | Responsibility | Must not depend on |
|---------|--------------|----------------|--------------------|
| Error taxonomy | `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayErrorCode.java` | Static public status/code/safe-message vocabulary | Rate-limit algorithm, Redis, client identity, quota state |
| Error renderer | Existing `GatewayHttpErrorWriter.java` | Render the stable body and correlation value | Feature-specific handlers or downstream DTOs |
| Contract tests | `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/` | Freeze status/body/trace compatibility | Real Redis or external services |
| Tracing technology guide | `docs/technology/technology-problem-map.md` | Record instrumentation/bridge/export/Collector ownership | Claiming runtime tracing is already installed |

**Dependency direction check**: The Gateway keeps its lean responsibility-oriented ADR 0003 structure. A future `ratelimit` handler may select the enum and delegate rendering, but the shared error package will not depend back on `ratelimit`.

## Flow and Failure Boundaries

### Flow 1 - Contract rendering in this feature

1. A contract test selects `RATE_LIMIT_EXCEEDED` directly.
2. The existing writer resolves the current Feature 011 correlation value.
3. The writer returns HTTP 429 and the fixed safe three-field JSON body.

### Future flow - explicitly not implemented

1. A future Gateway rate limiter evaluates an approved key/quota policy.
2. Only a deliberate rejection selects `RATE_LIMIT_EXCEEDED` and delegates to the writer.
3. The future feature owns `Retry-After`, quota headers, backend failure policy, metrics, and load evidence.

**Sequence/ordering guarantee**: N/A.
**Timeout/retry behavior**: Not selected.
**Duplicate/replay behavior**: N/A.

## Data, Transactions, and Concurrency

- **Source of truth**: N/A.
- **Transaction boundary**: N/A.
- **Hot-path state**: Not introduced.
- **Concurrency control**: Deferred with actual limiter implementation.
- **Idempotency record**: N/A.
- **Outbox/inbox**: N/A.
- **Migration**: N/A.

## Contracts and Compatibility

| Contract | Owner | Consumers | Version/change | Compatibility and rollout |
|----------|-------|-----------|----------------|---------------------------|
| Gateway rate-limit error HTTP contract | `api-gateway` | Public clients | Add HTTP 429 `RATE_LIMIT_EXCEEDED` | Additive; deployable before any limiter selects it |

**Contract file path**: `specs/012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md`

**Breaking-change decision**: None. Feature 011's seven rows and all downstream pass-through behavior remain unchanged.

## Failure, Retry, and Compensation

| Failure mode | Detection | Client outcome | Retry/compensation | Recovery | Evidence |
|--------------|-----------|----------------|--------------------|----------|----------|
| Deliberate future quota rejection | Future limiter decision | Approved 429 envelope | No policy selected here | Future limiter feature | Taxonomy/writer tests |
| Downstream returns 429 | HTTP response obtained | Original downstream status/body | Downstream-owned | Downstream-owned | Existing proxy pass-through test |
| Writer response already committed | Existing committed check | No second write | Original failure propagates | Existing behavior | Existing Feature 011 tests |

## Security and Abuse Controls

- **Authentication/authorization**: Unchanged.
- **Trust boundaries**: The body contains no caller identity, rate-limit key, quota state, Redis detail, or raw exception.
- **Sensitive data**: Existing safe-message and logging rules remain.
- **Abuse controls**: Contract only; no request is actually rate limited.
- **Auditability**: Existing error observation records safe code/status/path/correlation fields.

## Observability and Operations

- **Health**: Unchanged.
- **Metrics**: Existing Actuator/Prometheus configuration unchanged; rate-limit counters are deferred.
- **Tracing**: Current `X-Trace-Id`/UUID behavior is correlation, not a complete distributed trace implementation. The approved future standard is Micrometer Tracing as the application API, OpenTelemetry bridge plus OTLP exporter, W3C propagation, and a root-owned OpenTelemetry Collector. A runtime tracing feature must prefer the active Micrometer span trace ID and retain an explicit safe fallback for very early failures/tests.
- **Logs**: Existing safe Gateway error observation remains; no raw limiter state is added.
- **Alerts/SLOs**: Deferred with runtime limiter and tracing features.
- **Runbook/reconciliation**: N/A.

## Migration, Rollout, and Rollback

1. Publish the additive contract and tracing-stack clarification.
2. Add the enum member after its failing contract tests exist.
3. Verify all Gateway tests before any later limiter integration.

**Rollback trigger**: Any existing Gateway contract regression.
**Rollback procedure**: Revert the additive enum/test/docs group; no data or infrastructure rollback exists.
**Irreversible step**: None.

## Verification Strategy and Evidence

| Requirement/risk | Verification type | Command | Expected evidence |
|------------------|-------------------|---------|-------------------|
| FR-001 through FR-004, NFR-001 | Unit/contract | `.\mvnw.cmd -pl services/api-gateway -am "-Dtest=GatewayErrorCodeTests,GatewayHttpErrorWriterTests" test` | Exact eighth taxonomy row and 429 body/status/content type/trace pass |
| FR-005, NFR-002 | Regression/contract | `.\mvnw.cmd -pl services/api-gateway -am "-Dtest=GatewayProxyPassThroughTests" test` | Downstream responses including 429 remain byte-for-byte owned by downstream |
| All code changes | Module reactor | `.\mvnw.cmd -pl services/api-gateway -am verify` | Exit 0; all Gateway tests pass |
| FR-006 through FR-008, NFR-003 | Documentation review | Inspect feature artifacts and `docs/technology/technology-problem-map.md` | No limiter/tracing runtime claim or inferred quota; tracing layers are distinct |

**Required module build**: `.\mvnw.cmd -pl services/api-gateway -am verify`
**Required full build**: N/A; production change is isolated to one independently buildable service and one documentation guide.
**Kubernetes validation**: N/A; no overlay changes.
**Load/failure validation**: N/A; no limiter executes in this feature.

## Project Structure

```text
specs/012-gateway-rate-limit-contract/
├── spec.md
├── plan.md
├── research.md
├── quickstart.md
├── validation.md
├── contracts/
│   └── gateway-rate-limit-error-http.md
├── checklists/
│   └── requirements.md
└── tasks.md

services/api-gateway/src/
├── main/java/com/philia/flashsale/gateway/error/GatewayErrorCode.java
└── test/java/com/philia/flashsale/gateway/error/
    ├── GatewayErrorCodeTests.java
    └── GatewayHttpErrorWriterTests.java

docs/technology/technology-problem-map.md
```

**Structure decision**: Extend the existing Gateway error boundary; do not create a `ratelimit` type until an actual limiter capability is approved.

## Complexity Tracking

No constitutional violation, waiver, or accepted extra complexity.
