# Implementation Plan: Clean Hexagonal Service Scaffold

**Branch**: `002-clean-hex-scaffold` | **Date**: 2026-07-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-clean-hex-scaffold/spec.md`

## Summary

Initialize a visible Clean Architecture and Hexagonal Architecture scaffold for the existing nine Spring Boot service modules without adding business behavior. The implementation adds version-controlled marker files for empty architectural packages, a repository architecture guide, and feature artifacts that demonstrate the Spec Kit workflow. Existing service entry points, configuration, dependencies, and tests remain unchanged.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 3.x service dependencies only; no new production dependency

**Storage**: N/A - no schema, migration, repository, or persistence implementation is added

**Testing**: Static scaffold inspection plus existing Maven context tests

**Target Platform**: Existing Maven monorepo with independently runnable Spring Boot services

**Project Type**: Java microservice monorepo

**Performance Goals**: N/A - no runtime behavior is introduced

**Constraints**: Do not create full classes; do not introduce shared domain, shared JPA, business API, Kafka event, Redis Lua script, migrations, or infrastructure deployment assets

**Scale/Scope**: Nine service modules, one architecture guide, and one Spec Kit feature directory

## Constitution Check

*GATE result before Phase 0 research: PASS. Re-check after Phase 1 design: PASS.*

- **Specification traceability**: This plan implements FR-001 through FR-009 and leaves future business behavior to later features.
- **Service ownership**: The scaffold creates package locations only. It does not share domain models, JPA entities, repositories, or schemas.
- **Communication**: No HTTP route, service client, Kafka topic, event consumer, or event contract is added.
- **Data and messaging**: PostgreSQL, Redis, Kafka, idempotency, outbox, retry, ordering, and reconciliation remain future implementation concerns.
- **Root infrastructure ownership**: No root `infra/` asset is changed. Service source structure remains under `services/<service>/`.
- **Observability**: Existing Actuator and Prometheus configuration from the scaffold baseline remains unchanged. Future tracing packages are documented as extension points, not implemented.
- **Contracts and dependencies**: No production dependency is added. The only contract in this feature is a scaffold convention contract for reviewers.
- **Validation**: Static inspection applies. Maven verification can be run because non-Java marker files should not affect compilation. No database, contract, load, Redis, Kafka, or Kubernetes validation applies.
- **Architecture decisions**: No ADR is required because this does not change service boundaries, discovery, ingress, persistence ownership, communication style, durability, or deployment coupling.

## Project Structure

### Documentation (this feature)

```text
specs/002-clean-hex-scaffold/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── service-structure.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
docs/
└── architecture/
    └── service-clean-hex-structure.md

services/<service>/src/main/java/com/philia/flashsale/<service-package>/
├── domain/
├── application/
├── adapter/
└── configuration/
```

Core and medium business services use this expanded scaffold:

```text
domain/
├── model/
├── valueobject/
├── policy/
├── event/
└── exception/
application/
├── command/
├── result/
├── usecase/
└── port/
    ├── in/
    └── out/
adapter/
├── in/
│   ├── web/
│   └── messaging/
└── out/
    ├── persistence/
    ├── messaging/
    └── http/
configuration/
```

`flashsale-service` adds explicit future adapter locations:

```text
adapter/out/
├── redis/
├── outbox/
└── time/
```

`api-gateway` stays lean:

```text
domain/
├── policy/
└── exception/
application/
├── usecase/
└── port/
    ├── in/
    └── out/
adapter/
├── in/web/
└── out/http/
configuration/
```

**Structure Decision**: Use package-level Clean/Hexagonal structure inside each service module. Do not create Maven modules per layer. Do not extract shared libraries in this feature. Empty packages are kept through marker files only.

## Dependency Direction

- `domain` contains service-owned business concepts and depends on no Spring, JPA, Redis, Kafka, HTTP, or shared service module.
- `application` orchestrates use cases and depends on domain plus business-capability ports.
- `adapter` implements inbound and outbound technology integration and depends inward on application and domain.
- `configuration` wires adapters and application components and may depend on adapter and application packages.
- Reverse dependencies are prohibited.

## Port Adjustment

Ports must describe business capabilities:

- Prefer `ReserveQuotaPort`, `LoadActiveSalePort`, `PersistReservationWithOutboxPort`, `PublishReservationEventPort`, `LoadPaymentAttemptPort`, or `SendNotificationPort`.
- Avoid `RedisGetPort`, `RedisSetPort`, `KafkaSendPort`, `JpaSavePort`, or other vendor/API-shaped ports.
- When a durable state change and event publication must be reliable, prefer a port that preserves the transactional boundary, such as `PersistReservationWithOutboxPort`, rather than separate save and publish ports that can drift.

## Complexity Tracking

No constitutional violation or architecture exception is required for this feature.
