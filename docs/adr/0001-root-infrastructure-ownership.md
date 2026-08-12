# ADR 0001: Root Infrastructure Ownership

**Status**: Accepted

**Date**: 2026-07-11

## Context

The monorepo contains independently deployable Spring Boot services and root-level directories for
shared platform work. Without an explicit boundary, shared deployment assets can drift into a
service module, while service-owned runtime configuration or migrations can be moved into a central
infrastructure folder. Both outcomes weaken service autonomy and make ownership unclear.

Observability creates a common point of confusion. A service must expose metrics, but the platform
must operate the Prometheus and Grafana components that consume those metrics. Treating both sides
as the same kind of infrastructure would place the wrong artifacts together.

## Decision

Repository-wide platform and environment assets are owned by the root `infra/` directory:

- `infra/docker/` owns shared local backing-service orchestration and bootstrap assets.
- `infra/k8s/` owns first-party Kubernetes bases and environment overlays.
- `infra/helm/` owns approved Helm charts or third-party values. A feature plan must define the
  source of truth so Helm and Kustomize do not describe the same resource independently.
- `infra/monitoring/` owns Prometheus scrape infrastructure, Grafana provisioning and dashboards,
  and alert rules.

Each `services/<service>/` module owns the artifacts required to build, run, test, and evolve that
service independently: application source, POM dependencies, `application*.yml`, tests, and its own
database migrations. A service-specific image build recipe may remain with the service. Shared
container orchestration belongs under `infra/docker/`.

Infrastructure may provision isolated database instances or databases and reference service
secrets, but service business-schema migrations remain with the owning service. Cross-service
database access remains prohibited.

For Spring Boot 3.x observability:

- Each service owns its Actuator dependency, runtime `micrometer-registry-prometheus` dependency,
  and declarative endpoint exposure.
- Spring Boot auto-configures the registry from the runtime classpath.
- Service code does not construct a `PrometheusMeterRegistry` or couple business logic to the
  Prometheus registry implementation.
- Prometheus/Grafana deployment, scrape discovery, dashboards, and alerts belong under
  `infra/monitoring/`.

Any exception requires justification in the active implementation plan and an approved ADR.

This decision establishes ownership and the initial documented directory structure only. It does
not select or implement Docker Compose, Kubernetes resources, Helm releases, Prometheus, Grafana,
databases, brokers, caches, or production environments.

## Consequences

- A clean clone has an explicit, reviewable location for future shared infrastructure work.
- Service runtime capabilities stay independently buildable and deployable.
- Platform teams can evolve environments without taking ownership of service business schemas.
- Infrastructure features must document whether Kubernetes or Helm owns each deployed resource.
- Initial directories need documentation files because Git does not preserve empty directories.
- A small amount of boundary review is required whenever an asset could reasonably be service-owned
  or platform-owned.

## Alternatives considered

### Put all infrastructure-related artifacts inside each service

Rejected because shared Kafka, PostgreSQL, Redis, Kubernetes topology, and monitoring assets would
be duplicated or arbitrarily owned by one service.

### Put every operational artifact under root `infra/`

Rejected because service dependencies, runtime configuration, database migrations, and
service-specific image builds are part of independent service ownership.

### Keep only empty root directories and rely on convention

Rejected because Git does not preserve empty directories and an undocumented convention does not
define ownership or exception handling.
