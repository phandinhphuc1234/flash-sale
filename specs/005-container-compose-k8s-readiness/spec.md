# Feature Specification: Container Compose and Kubernetes Readiness

**Feature Branch**: `[005-container-compose-k8s-readiness]`

**Created**: 2026-07-14

**Status**: Draft

**Input**: User description: "Tạo spec, docs và triển khai Docker/Compose theo hướng dễ mở rộng sang Kubernetes; docs phải giải thích phần mở rộng sang K8s và quyết định có nên tách compose prod/dev hay không."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a local platform baseline (Priority: P1)

As a developer, I want one shared local Compose topology for platform dependencies so that I can start PostgreSQL, Redis, and Kafka consistently without each service inventing its own local environment.

**Why this priority**: The backing services are the first local runtime dependency for future service development and must follow the repository's root infrastructure ownership rule.

**Independent Test**: Validate the Compose model and start only the platform services from `infra/docker` without requiring application image builds.

**Acceptance Scenarios**:

1. **Given** a clean repository checkout, **When** a developer renders the Compose configuration, **Then** the platform services and their shared network are valid.
2. **Given** the developer wants only backing services, **When** the developer starts PostgreSQL, Redis, and Kafka, **Then** no application service image build is required.

---

### User Story 2 - Build services as independently deployable images (Priority: P2)

As a developer, I want every Spring Boot service to have an independent image build recipe so that each service can be built, tested, tagged, and later deployed separately.

**Why this priority**: Independent image builds preserve microservice deployability and prepare the monorepo for CI/CD and Kubernetes.

**Independent Test**: Inspect every service for an image recipe and validate the app Compose profile that builds service containers from the repository root context.

**Acceptance Scenarios**:

1. **Given** any service under `services/`, **When** a developer looks for its image recipe, **Then** the recipe exists under that service.
2. **Given** a developer wants to run the application topology locally, **When** the app profile is enabled, **Then** all service containers share internal DNS names and only the gateway is public by default.

---

### User Story 3 - Understand the Kubernetes migration path (Priority: P3)

As a developer new to microservices, I want documentation that explains how local Compose maps to Kubernetes so that I can avoid treating Compose as the production deployment model.

**Why this priority**: The project constitution targets Kubernetes Service and DNS; documentation prevents premature `compose.prod.yml` drift and prepares future Kustomize work.

**Independent Test**: Read the deployment documentation and verify it explains Dockerfile ownership, Compose file layering, environment configuration, probes, service discovery, migration jobs, and the future `infra/k8s` shape.

**Acceptance Scenarios**:

1. **Given** a developer asks whether to create `compose.prod.yml`, **When** they read the docs, **Then** the answer is that production should be Kubernetes and Compose production files are not introduced for this repo.
2. **Given** a future feature introduces Kubernetes manifests, **When** the team follows the docs, **Then** Compose service names, env variables, health probes, and image names have a clear mapping to Kubernetes Deployments, Services, ConfigMaps, Secrets, and Jobs.

---

### Edge Cases

- A developer runs Compose from the repository root or from `infra/docker`; documented commands must work by explicitly passing the Compose file paths.
- Application services currently do not contain JDBC/JPA dependencies; the Compose app profile must not force startup-time database migrations in the current skeleton.
- Real secrets must not be committed; examples may show safe placeholder values only.
- A future production environment must not use Docker Compose as the source of truth when Kubernetes manifests become available.
- A service may need temporary host port exposure for debugging; default Compose must still keep non-gateway services internal.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Each service under `services/` MUST have a service-local image build recipe.
- **FR-002**: Shared local orchestration MUST live under `infra/docker/` and MUST NOT be duplicated inside service modules.
- **FR-003**: The Compose baseline MUST include local PostgreSQL, Redis, and Kafka platform services.
- **FR-004**: The Compose application topology MUST use stable internal service names that match future Kubernetes service names.
- **FR-005**: The default local application topology MUST expose only `api-gateway` to the host; other services remain internal unless a development override is explicitly used.
- **FR-006**: Runtime configuration MUST be provided through environment variables with committed placeholder examples only.
- **FR-007**: Real local secret files MUST remain ignored by git, while example environment files MUST be commit-friendly.
- **FR-008**: Local database provisioning MAY create isolated logical databases for services, but MUST NOT contain business-schema migrations.
- **FR-009**: Application containers MUST disable Liquibase in the current skeleton because no service has an approved database schema or JDBC runtime yet.
- **FR-010**: Documentation MUST explain why this repo uses `compose.yml` plus development overrides/profiles rather than `compose.prod.yml`.
- **FR-011**: Documentation MUST map Compose concepts to future Kubernetes concepts: service names, images, environment variables, probes, ConfigMaps, Secrets, Jobs, Deployments, and Services.
- **FR-012**: Validation instructions MUST include Compose configuration rendering for both baseline and development override files.

### Key Entities

- **Service Image Recipe**: The service-owned instructions that produce one deployable container image for a service.
- **Compose Topology**: The root-owned local orchestration model for platform and application containers.
- **Development Override**: Optional Compose settings that expose additional service ports for local debugging.
- **Runtime Configuration Variable**: Environment-provided value that can later map to a Kubernetes ConfigMap or Secret.
- **Kubernetes Migration Mapping**: Documentation that translates local Compose choices to future Kubernetes resources.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can identify the image build recipe for all 9 services in under 2 minutes.
- **SC-002**: Compose configuration rendering succeeds for the baseline file and the development override combination.
- **SC-003**: The default Compose topology has exactly one public application entry point: `api-gateway`.
- **SC-004**: Documentation answers the `compose.prod.yml` question and points production deployment toward Kubernetes without requiring additional explanation.
- **SC-005**: Future Kubernetes planning can reuse documented service names, health endpoints, and environment variable conventions without renaming the local topology.

## Assumptions

- Docker Compose is for local development and integration testing, not the repository's production deployment source of truth.
- Future production and staging deployment will use Kubernetes resources under `infra/k8s/`.
- One local PostgreSQL container with multiple logical databases is acceptable for the initial developer baseline.
- Real database schemas and service persistence dependencies will be introduced by future service features.
- Observability backends remain planned; this feature only preserves service Actuator endpoints and documents the future mapping.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Dockerfiles stay with each service; service runtime configuration and migrations remain in `services/<service>/`. No service accesses another service database.
- **External ingress**: Default local application traffic enters through `api-gateway`; non-gateway service ports are not public unless the development override is used.
- **API/event contracts**: No HTTP or Kafka business contract changes are introduced.
- **Durable and hot-path data**: Local PostgreSQL, Redis, and Kafka containers are provisioned as platform services only. No business schema, Redis Lua script, or durable business state is added.
- **Messaging reliability**: Kafka is introduced only as a local backing-service container; no producers, consumers, idempotency behavior, outbox, retry, ordering, or recovery logic is implemented.
- **Root infrastructure ownership**: Shared Compose topology and platform bootstrap assets live under `infra/docker/`; service image recipes live under each service as allowed by ADR 0001.
- **Observability**: Existing service liveness, readiness, info, and Prometheus endpoints remain declarative. No manual Prometheus registry is introduced. K8s probe mapping is documented.
- **Verification**: Required validation is Compose configuration rendering for baseline and dev override. Maven verification is not required because no Java source or POM dependencies change.
- **Architecture decisions**: No new ADR is required; the feature implements ADR 0001 and does not change service boundaries, ingress ownership, discovery rules, or deployment target.
