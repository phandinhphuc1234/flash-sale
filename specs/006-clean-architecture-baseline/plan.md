# Implementation Plan: Clean Architecture Working Baseline

**Branch**: `[006-clean-architecture-baseline]` | **Date**: 2026-07-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-clean-architecture-baseline/spec.md`

**Plan status**: Approved

## Summary

Refine the existing marker-only Clean/Hexagonal scaffold without beginning a business feature. Add an empty `application/query` location to the eight business services, expand the repository architecture guide into a practical placement reference, link it from the root README, and validate that the repository still contains only the nine application entry points and nine context-startup tests. Nested adapter packages remain create-on-demand; `api-gateway` stays lean; Product Catalog behavior is explicitly out of scope.

## Technical Context

**Language/Version**: Java 21; no Java source change

**Framework**: Existing Spring Boot 3.x service modules; no framework configuration change

**Build**: Existing Maven wrapper and nine-service reactor

**Primary Dependencies**: Existing dependencies only; no dependency addition

**Storage**: N/A; service-owned Liquibase master files remain empty and unchanged

**Communication**: N/A; no HTTP route, service client, or Kafka contract is introduced

**Testing**: Static marker topology and exact Java-source inventory checks, documentation checks, and `./mvnw clean verify`

**Target Platform**: Repository source tree; no Docker or Kubernetes runtime change

**Performance Goals**: N/A; no runtime behavior

**Constraints**: Marker and documentation changes only; no product endpoint; no speculative DTO/entity/mapper/security/scheduler implementation; no rewrite of completed feature history

**Scale/Scope**: Eight new empty marker files, one living architecture guide update, one README link, and one complete Spec Kit feature set

## Risk Classification

| Dimension | Level | Evidence | Required mitigation/verification |
|-----------|-------|----------|----------------------------------|
| Money/payment | Low | No payment behavior or data changes | Scope audit |
| Inventory/concurrency | Low | No stock, reservation, Redis, or concurrent code | Scope audit |
| Security/privacy | Low | No authentication, token, configuration, or data changes | Source/config audit |
| Distributed consistency | Low | No state transition or messaging | Contract and migration absence audit |
| Contract/compatibility | Low | Reviewer-facing structure contract only | Confirm no HTTP/Kafka contract change |
| Migration/rollback | Low | Empty marker and Markdown changes are reversible | File inventory |
| Load/operability | Low | No runtime path changes | Full Maven regression build |

**Overall risk**: Low. The main risk is architecture drift through speculative folders or accidentally beginning the Product feature.

**Selected test ordering**: Static scope checks after each coherent marker/documentation group, followed by the full Maven build.

## Constitution Check

*GATE result before research: PASS. Re-check after design: PASS.*

- **Specification traceability**: PASS. The marker delta maps to FR-001 through FR-003; documentation maps to FR-004 through FR-007; scope and validation map to FR-008 through FR-011.
- **Service ownership**: PASS. No domain model, schema, JPA type, repository, or cross-service access is introduced.
- **Communication**: PASS. No ingress, discovery, HTTP, Kafka, or gRPC behavior changes.
- **Data and messaging**: PASS. PostgreSQL, Redis, Kafka, idempotency, outbox, retry, and reconciliation are untouched.
- **Infrastructure ownership**: PASS. No root or service infrastructure asset changes.
- **Observability**: PASS. Existing Actuator and declarative Prometheus configuration stays unchanged; no registry or tracing code is added.
- **Dependencies/contracts**: PASS. No Maven dependency or business contract changes. `contracts/package-placement.md` is a reviewer-facing repository convention only.
- **Validation**: PASS. Exact source inventory and the full cross-cutting Maven build are required. Runtime, contract, migration, load, and Kubernetes checks are omitted because their artifacts do not change.
- **Architecture decisions**: PASS. This refines the approved feature 002 convention and does not change an architectural boundary; no ADR is required.

## Architecture and Package Mapping

### Business-service baseline

The existing eight business-service packages remain:

```text
domain/
  model/ valueobject/ policy/ event/ exception/
application/
  command/ query/ result/ usecase/ port/in/ port/out/
adapter/
  in/web/ in/messaging/
  out/persistence/ out/http/ out/messaging/
configuration/
```

Only `application/query/.gitkeep` is new. The other locations already exist from feature 002.

### Gateway baseline

The gateway remains unchanged:

```text
domain/policy/ domain/exception/
application/usecase/ application/port/in/ application/port/out/
adapter/in/web/ adapter/out/http/
configuration/
```

It receives no persistence, business model, query, or messaging marker.

### Create-on-demand boundary

The architecture guide describes future nested locations such as `adapter/in/web/dto`, `adapter/out/persistence/entity`, or `configuration/security`, but those directories are created only with an approved use case that places a real type there. This keeps the baseline understandable without implying technologies or responsibilities that a service may never need.

### Dependency direction

```text
adapter -> application -> domain
configuration -> adapter + application
```

Domain remains framework-free. Application owns use-case models and capability ports, never concrete adapters. Adapter-local transport and persistence representations do not cross into the domain.

## Scope Protection

This feature does not create any of the following:

- `ProductController`, `GetProductUseCase`, product request/response DTO, domain model, entity, repository, migration, or gateway route
- controller, listener, scheduler, service implementation, port interface, adapter implementation, configuration class, or mapper for any service
- JPA, validation, Kafka, Redis, security, mapping, resilience, or HTTP-client dependency
- HTTP/OpenAPI or Kafka business contract

The exact Java allowlist remains the nine `*Application.java` entry points and nine `*ApplicationTests.java` context tests.

## Verification Strategy and Evidence

| Requirement/risk | Verification | Command or evidence | Expected result |
|------------------|-------------|---------------------|-----------------|
| FR-001/FR-002 | Marker topology | PowerShell `Test-Path` over the eight mapped service packages | Eight empty query markers exist |
| FR-003 | Gateway audit | `Test-Path` for prohibited gateway query/persistence markers | Paths remain absent |
| FR-004-FR-007 | Documentation audit | `rg` for placement headings, dependency direction, create-on-demand, and README link | Required rules found |
| FR-008-FR-010 | Exact Java/source scope audit | Enumerate `services/**/*.java`; search for product/controller/entity/route patterns | Exactly 18 existing Java files; no prohibited behavior |
| FR-011 | Cross-cutting regression | `.\mvnw.cmd clean verify` | Reactor succeeds for all modules |

**Required full build**: `.\mvnw.cmd clean verify` because the change spans all business-service source trees, even though it adds no compilable source.

**Not applicable**: Docker Compose, database migration, HTTP smoke, Kafka, Redis, load, and Kubernetes validation.

## Project Structure

### Documentation (this feature)

```text
specs/006-clean-architecture-baseline/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── package-placement.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Repository paths affected

```text
README.md
docs/architecture/service-clean-hex-structure.md
services/{authentication,product,campaign,flashsale,order,payment,notification,chatting}-service/
└── src/main/java/com/philia/flashsale/<context>/application/query/.gitkeep
```

**Structure decision**: Keep the established package-level architecture within independently deployable services. Add only the one missing generic use-case input category; document all technology-specific nesting as create-on-demand.

## Complexity Tracking

No constitutional violation, waiver, new dependency, ADR, or accepted complexity is required.

