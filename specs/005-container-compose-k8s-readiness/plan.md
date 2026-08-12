# Implementation Plan: Container Compose and Kubernetes Readiness

**Branch**: `[005-container-compose-k8s-readiness]` | **Date**: 2026-07-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-container-compose-k8s-readiness/spec.md`

## Summary

Introduce a local container runtime baseline for the Flash Sale monorepo: every service gets a service-local Dockerfile, shared local orchestration lives in `infra/docker/`, default Compose exposes only `api-gateway`, and deployment documentation explains how the local topology maps to future Kubernetes resources. Docker Compose is kept as a local/dev tool; production deployment remains Kubernetes under future `infra/k8s` overlays.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.x services

**Primary Dependencies**: Dockerfile, Docker Compose v2, Maven Wrapper, existing Spring Boot Actuator endpoints

**Storage**: Local PostgreSQL container provisions logical service databases; Redis and Kafka containers are local backing services only. No business schema is added.

**Testing**: `docker compose config` for baseline and development override; Docker image build commands are documented but not required to pull/build images during static validation.

**Target Platform**: Local Docker Desktop or compatible Docker Engine for development; future Kubernetes under `infra/k8s/`.

**Project Type**: Maven monorepo containing independently deployable Spring Boot microservices.

**Performance Goals**: Local topology should allow developers to start platform services independently and avoid unnecessary application image builds during normal single-service development.

**Constraints**: No service-local Compose files, no `compose.prod.yml`, no committed real secrets, no centralized service migrations under `infra/`, no Eureka, no public access path bypassing `api-gateway` by default.

**Scale/Scope**: 9 current Spring Boot services, 3 local platform dependencies, documentation for future Kubernetes migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Specification traceability**: PASS. Scope maps to FR-001 through FR-012 and does not add business behavior.
- **Service ownership**: PASS. Dockerfiles are service-owned build recipes; migrations and runtime configuration remain service-owned. No database access code is introduced.
- **Communication**: PASS. Compose service names mirror future Kubernetes DNS names. Public ingress remains through `api-gateway`.
- **Data and messaging**: PASS. PostgreSQL, Redis, and Kafka are platform containers only. No durable business schema, Redis Lua, Kafka consumer, or outbox logic is introduced.
- **Root infrastructure ownership**: PASS. Shared Compose files and local platform bootstrap live under `infra/docker/`; no shared orchestration is placed in a service.
- **Observability**: PASS. Existing Actuator health/readiness/liveness/prometheus endpoints are preserved and documented for Compose/K8s probe mapping. No registry bean is added.
- **Contracts and dependencies**: PASS. No new Maven production dependency is introduced. External container images are infrastructure choices documented in this plan.
- **Validation**: PASS. Compose configuration rendering covers changed infrastructure. Maven verification is not required because Java source and POM files are unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/005-container-compose-k8s-readiness/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── container-runtime.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
services/
├── api-gateway/Dockerfile
├── authentication-service/Dockerfile
├── product-service/Dockerfile
├── campaign-service/Dockerfile
├── flashsale-service/Dockerfile
├── order-service/Dockerfile
├── payment-service/Dockerfile
├── notification-service/Dockerfile
└── chatting-service/Dockerfile

infra/
└── docker/
    ├── compose.yml
    ├── compose.dev.yml
    ├── .env.example
    ├── README.md
    └── postgres/
        └── init/
            └── 01-create-databases.sql

docs/
└── deployment/
    ├── README.md
    └── container-compose-k8s-strategy.md
```

**Structure Decision**: Service image recipes stay with each service for independent image builds. Shared Compose files and local platform bootstrap assets stay under `infra/docker/`. Kubernetes manifests are not implemented in this feature; docs describe the future `infra/k8s/base` and `infra/k8s/overlays` path.

## Complexity Tracking

No constitutional violations or complexity exceptions are required.

## Phase 0 Research Summary

See [research.md](research.md).

## Phase 1 Design Summary

- Artifact entities are captured in [data-model.md](data-model.md).
- Runtime/validation contracts are captured in [contracts/container-runtime.md](contracts/container-runtime.md).
- Runnable validation is captured in [quickstart.md](quickstart.md).
