# Technology Documentation

This folder explains why each technology exists in the Flash Sale Engine architecture.

Start here:

- [Technology problem map](technology-problem-map.md)
- [Service communication protocols](../architecture/service-communication-protocols.md)
- [Liquibase migration rules](liquibase-migration-rules.md)
- [Architecture diagrams](../architecture/diagrams/README.md)

## Status Labels

`Current` means the repository already contains build dependencies, configuration, or source scaffold for the technology.

`Planned` means the Constitution, architecture docs, or service scaffold expects the technology, but no production implementation exists yet.

`Deferred` means the idea is visible in architecture discussion or reference diagrams, but it is not approved as a repository baseline yet.

## Current Baseline

The current repository baseline is intentionally small:

- Java 21
- Maven multi-module monorepo
- Spring Boot 3.5.x service shells
- Spring Cloud Gateway WebFlux in `api-gateway`
- Spring Web in non-gateway services
- Spring Boot Actuator
- Micrometer Prometheus registry dependency
- Service-local Dockerfiles
- Docker Compose local platform orchestration under `infra/docker/`
- Clean/Hexagonal package scaffold
- Spec Kit-style feature artifacts

Kafka, Redis, and PostgreSQL are available as local Docker platform containers, but service code
adapters and business persistence are still planned. Kubernetes manifests, OpenTelemetry tracing,
Loki, Tempo, Grafana dashboards, and gRPC are not implemented by the current service code. They
require future approved features before production use.

For local container runtime and the future Kubernetes path, see
[Container Compose and Kubernetes strategy](../deployment/container-compose-k8s-strategy.md).
