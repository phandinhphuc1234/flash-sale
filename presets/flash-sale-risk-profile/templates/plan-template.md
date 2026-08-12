# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`
**Plan status**: Draft | **Technical owner**: [OWNER] | **Required reviewers**: [REVIEWERS]

> This plan translates approved requirements into implementation decisions. It MUST NOT add or alter
> business behavior. Return any missing or conflicting behavior to `spec.md` for clarification and
> re-approval before implementation.

## Summary

[Approved requirement delta, affected user stories, and concise technical approach]

## Technical Context

**Language/Version**: Java 21
**Framework**: Spring Boot 3.x
**Build**: Maven wrapper; independent modules under `services/<service>/`
**Primary dependencies**: [existing dependencies and justified additions, or NEEDS CLARIFICATION]
**Storage**: [owning PostgreSQL schema, Redis hot path, or N/A]
**Communication**: [documented HTTP and/or versioned Kafka contracts, or N/A]
**Testing**: [unit, integration, contract, load, migration, Kubernetes validation as applicable]
**Target platform**: Kubernetes
**Performance goals**: [approved measurable target or NEEDS CLARIFICATION]
**Constraints**: [latency, correctness, availability, security, compatibility, or NEEDS CLARIFICATION]
**Scale/scope**: [approved volume and affected services]

## Risk Classification

| Dimension | Level (low/medium/high/critical) | Evidence | Required mitigation/verification |
|-----------|----------------------------------|----------|----------------------------------|
| Money/payment | [level] | [spec refs] | [plan or N/A] |
| Inventory/concurrency | [level] | [spec refs] | [plan or N/A] |
| Security/privacy | [level] | [spec refs] | [plan or N/A] |
| Distributed consistency | [level] | [spec refs] | [plan or N/A] |
| Contract/compatibility | [level] | [spec refs] | [plan or N/A] |
| Migration/rollback | [level] | [spec refs] | [plan or N/A] |
| Load/operability | [level] | [spec refs] | [plan or N/A] |

**Overall risk**: [level and rationale]
**Selected test ordering**: [test-first for specified risks / risk-based ordering]
**Approval gates**: [required reviewers and evidence before implementation/release]

## Constitution Check

*GATE: Must pass before research. Re-check after design and before task generation.*

- **Specification traceability**: Every design decision maps to approved FR/NFR/INV/AC scope; no blocking clarification remains.
- **Service ownership**: Persistence, domain model, JPA entities, repositories, and database access remain owned by one service; boundary changes have an ADR.
- **Communication**: External ingress uses api-gateway; discovery uses Kubernetes Service/DNS; HTTP and Kafka contracts are documented and versioned as required.
- **Data and messaging**: PostgreSQL remains durable truth; Redis Lua is limited to atomic hot-path operations; consumer idempotency, outbox, ordering, retry, and reconciliation are explicit where applicable.
- **Infrastructure ownership**: Shared Docker/Kubernetes/Helm/monitoring assets live under root `infra/`; runtime configuration and migrations remain with the owning service.
- **Observability**: Liveness, readiness, declaratively exposed Prometheus metrics, and trace-ID propagation are planned; no manual Prometheus registry bean is introduced.
- **Dependencies/contracts**: Production dependency additions and API/event compatibility impacts are justified and documented.
- **Validation**: Applicable unit, integration, contract, load, migration, Maven, and Kubernetes checks have commands and acceptance evidence; omissions are justified.

**Gate result**: [PASS / FAIL]
**Violations or waivers**: [ADR/approval reference, or none]

## Context and Service Ownership

| Capability/data | Owning context/service | Readers/callers | Allowed interaction | Prohibited interaction |
|-----------------|------------------------|-----------------|---------------------|------------------------|
| [capability] | [service] | [actors/services] | [HTTP/event] | [cross-service DB/shared entity/etc.] |

**Context-map change**: [relationship added/changed, or none]
**Boundary decision**: [why work belongs to the selected service(s)]

## Architecture and Hexagonal Mapping

<!--
  Map the affected code boundary; do not reorganize unrelated code. Hexagonal structure is applied
  by this risk profile to the affected slice, but a repo-wide mandate requires a Constitution change.
-->

| Concern | Planned package/path | Responsibility | Must not depend on |
|---------|----------------------|----------------|--------------------|
| Domain model/rules | [path] | [pure business concepts/invariants] | Spring, persistence, transport, vendor SDKs |
| Application use case | [path] | [orchestration and ports] | concrete adapters |
| Inbound port/adapter | [path] | [HTTP/Kafka/CLI mapping] | persistence internals |
| Outbound port/adapter | [path] | [database/cache/event/external client] | controller/transport concerns |
| Bootstrap/configuration | [path] | [Spring wiring and runtime config] | business decisions |

**Dependency direction check**: [How inward dependencies are enforced/tested]

## Synchronous and Asynchronous Flows

### Flow [N] - [Name]

1. [Actor/adapter receives command or event; trace/idempotency identity]
2. [Application use case and domain invariant evaluation]
3. [State transition and durability boundary]
4. [HTTP/event publication behavior]
5. [Observable success/failure outcome]

**Sequence/ordering guarantee**: [guarantee or N/A]
**Timeout/retry behavior**: [bounded policy and owner]
**Duplicate/replay behavior**: [required result]

## Data, Transactions, and Concurrency

- **Source of truth**: [service-owned PostgreSQL data]
- **Transaction boundary**: [atomic durable changes]
- **Hot-path state**: [Redis keys/Lua atomicity and reconciliation, or N/A]
- **Concurrency control**: [optimistic/pessimistic/unique constraint/atomic script and why]
- **Idempotency record**: [key, scope, storage, lifetime, replay response, or N/A]
- **Outbox/inbox**: [schema, publication/consumption lifecycle, cleanup, or N/A]
- **Migration**: [forward/backward compatibility and data backfill, or N/A]

## Contracts and Compatibility

| Contract | Producer/owner | Consumers | Version/change | Compatibility and rollout |
|----------|----------------|-----------|----------------|---------------------------|
| [HTTP/OpenAPI/Kafka schema] | [service] | [consumers] | [version/delta] | [strategy] |

**Contract file paths**: [exact paths]
**Breaking-change decision**: [none, or ADR/approval and migration]

## Failure, Retry, and Compensation

| Failure mode | Detection | User/business outcome | Retry/compensation | Recovery/reconciliation | Evidence |
|--------------|-----------|-----------------------|--------------------|-------------------------|----------|
| [mode] | [signal] | [required outcome] | [bounded action] | [owner/process] | [test/metric/runbook] |

## Security and Abuse Controls

- **Authentication/authorization**: [actors and permission checks]
- **Trust boundaries**: [external/internal inputs and validation]
- **Sensitive data**: [classification, minimization, encryption, logging exclusions]
- **Abuse controls**: [rate/quota/replay/fraud controls, or N/A]
- **Auditability**: [who/what/when evidence and retention]

## Observability and Operations

- **Health**: [liveness/readiness behavior and dependencies]
- **Metrics**: [business/technical metrics; declarative `/actuator/prometheus` exposure]
- **Tracing**: [trace ID propagation across important HTTP requests and Kafka events]
- **Logs**: [structured fields, redaction, correlation]
- **Alerts/SLOs**: [thresholds and owners]
- **Runbook/reconciliation**: [path and operator actions]

## Migration, Rollout, and Rollback

1. [Backward-compatible preparation]
2. [Deployment/data migration/event rollout order]
3. [Feature activation or traffic ramp]
4. [Post-deploy verification]

**Rollback trigger**: [measurable trigger]
**Rollback procedure**: [safe executable procedure]
**Irreversible step**: [none, or explicit approval and recovery plan]

## Verification Strategy and Evidence

| Requirement/risk | Verification type | Command/environment | Expected evidence | Task owner |
|------------------|-------------------|---------------------|-------------------|------------|
| [FR/INV/AC/risk] | [unit/integration/contract/load/manual] | [exact command] | [assertion/report/log] | [owner] |

**Required module build**: `./mvnw -pl services/<service> -am verify`
**Required full build**: [`./mvnw clean verify`, or N/A with rationale]
**Kubernetes validation**: [`kubectl apply --dry-run=client -k <overlay>`, or N/A]
**Load/failure validation**: [command/scenario and acceptance threshold, or N/A]

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
├── checklists/
└── tasks.md
```

### Source Code (affected paths only)

```text
services/<owning-service>/
├── pom.xml
└── src/
    ├── main/
    └── test/

infra/                       # shared platform assets only, when affected
```

**Structure decision**: [Concrete paths and why they preserve ownership/dependency direction]

## Complexity Tracking

> Fill only for Constitution Check violations, exceptions, or knowingly accepted complexity.

| Violation/complexity | Why needed | Simpler alternative rejected because | ADR/approval | Removal/review date |
|----------------------|------------|--------------------------------------|--------------|---------------------|
| [item] | [reason] | [trade-off] | [reference] | [date] |
