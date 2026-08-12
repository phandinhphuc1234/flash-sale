# Research: Container Compose and Kubernetes Readiness

## Decision: Use one base Compose file plus one development override; do not add `compose.prod.yml`

**Rationale**: Docker Compose supports multiple Compose files for environment and workflow customization, but the official docs also note that complexity moves into infrastructure/configuration as Compose layering grows. This repo's production target is Kubernetes, so a production Compose file would create a competing deployment source of truth. The cleaner split is:

- `infra/docker/compose.yml` for local platform and application topology.
- `infra/docker/compose.dev.yml` for optional local debugging conveniences such as exposing internal service ports.
- Future `infra/k8s/base` and `infra/k8s/overlays/<env>` for real deployment environments.

**Alternatives considered**:

- `compose.dev.yml` + `compose.prod.yml`: Rejected because `compose.prod.yml` would drift from Kubernetes and conflict with the repository constitution.
- One service-local Compose file per service: Rejected because shared backing services, networks, and ports would be duplicated and become inconsistent.
- A single large Compose file with every debug port always exposed: Rejected because the default topology should resemble Kubernetes ingress boundaries.

## Decision: Keep service Dockerfiles in each service

**Rationale**: Each service is independently deployable, so each service should have a local image build recipe. This follows ADR 0001: service-specific image build recipes may stay with services, while shared orchestration belongs under `infra/docker/`.

**Alternatives considered**:

- One root generic Dockerfile with a build arg: Rejected for now because the user wants to learn microservice containerization and service-local recipes are easier to inspect.
- Spring Boot buildpacks only: Deferred. Buildpacks are useful later, but Dockerfiles teach image stages, context, non-root execution, and CI image build mechanics more explicitly.

## Decision: Use multi-stage Dockerfiles

**Rationale**: Docker's multi-stage build guidance recommends separate build/runtime stages so build tools and intermediate artifacts do not land in the final image. This keeps service runtime images smaller and closer to production container practice.

**Alternatives considered**:

- Copy local `target/*.jar` into a runtime image: Rejected because it requires a prior host build and makes image builds less self-contained.
- Use full JDK image for runtime: Rejected because runtime images do not need compiler/build tooling.

## Decision: Use Compose service names that match future Kubernetes Services

**Rationale**: Kubernetes Services give stable names for pods behind a logical endpoint. Using names like `order-service` and `payment-service` in Compose trains developers to avoid `localhost` for container-to-container communication and makes future Kubernetes migration more direct.

**Alternatives considered**:

- Host-port URLs for service-to-service calls: Rejected because they do not work the same way inside containers or Kubernetes pods.
- Eureka/service discovery library: Rejected by constitution; Kubernetes DNS is the target discovery mechanism.

## Decision: Provision one local PostgreSQL container with multiple logical databases

**Rationale**: One local PostgreSQL container is lighter for a beginner-friendly local environment while preserving logical database-per-service ownership. Infrastructure may provision isolated databases; business schema migrations remain under each service.

**Alternatives considered**:

- One PostgreSQL container per service: More production-like, but heavier and noisier for the initial skeleton.
- One shared database/schema for all services: Rejected because it weakens service data ownership.

## Decision: Disable Liquibase inside current app containers

**Rationale**: The current service skeleton has Liquibase setup but no JDBC/JPA runtime or approved business schema migrations. Application containers should start without attempting database migrations. Future production should prefer a Kubernetes Job for migrations and run application replicas with app-time migrations disabled.

**Alternatives considered**:

- Keep `LIQUIBASE_ENABLED=true` in app containers: Rejected for the current skeleton because datasource dependencies are not implemented yet.
- Centralize migrations in `infra/docker`: Rejected by constitution and Liquibase rules.

## Decision: Map env examples to future ConfigMap and Secret ownership

**Rationale**: Kubernetes ConfigMaps are for non-confidential configuration and Secrets are for sensitive values. Keeping committed `.env.example` placeholders separate from ignored real `.env` files prepares a clean transition to ConfigMaps and Secrets.

**Alternatives considered**:

- Hard-code runtime values in service YAML: Rejected because it weakens environment portability.
- Commit real local credentials: Rejected for security.

## Official references used

- Docker Compose multiple files: https://docs.docker.com/compose/how-tos/multiple-compose-files/
- Docker Compose profiles: https://docs.docker.com/compose/how-tos/profiles/
- Docker multi-stage builds: https://docs.docker.com/build/building/multi-stage/
- Docker build best practices: https://docs.docker.com/build/building/best-practices/
- Kubernetes Services: https://kubernetes.io/docs/concepts/services-networking/service/
- Kubernetes ConfigMaps: https://kubernetes.io/docs/concepts/configuration/configmap/
- Kubernetes Secrets: https://kubernetes.io/docs/concepts/configuration/secret/
- Kubernetes probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Spring Boot OCI images/buildpacks: https://docs.spring.io/spring-boot/maven-plugin/build-image.html
