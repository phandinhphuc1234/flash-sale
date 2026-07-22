# Data Model: Container Compose and Kubernetes Readiness

This feature does not introduce business data. The entities below describe repository artifacts and operational configuration contracts.

## Service Image Recipe

- **Represents**: A service-owned Dockerfile that builds one Spring Boot service image.
- **Fields**:
  - `serviceName`: One of the current service module names.
  - `dockerfilePath`: `services/<service>/Dockerfile`.
  - `buildContext`: Repository root.
  - `mavenModule`: `services/<service>`.
  - `runtimePort`: `8080`.
- **Validation rules**:
  - Must not create a service-local Compose topology.
  - Must build only the owning service module with Maven `-pl services/<service> -am`.
  - Must use a runtime stage that does not include Maven build tooling.

## Compose Topology

- **Represents**: Shared local orchestration under `infra/docker/`.
- **Fields**:
  - `composeFile`: `infra/docker/compose.yml`.
  - `devOverrideFile`: `infra/docker/compose.dev.yml`.
  - `networkName`: Internal Compose network for all containers.
  - `platformServices`: PostgreSQL, Redis, Kafka.
  - `applicationServices`: 9 Spring Boot services.
- **Validation rules**:
  - Default topology exposes only `api-gateway` as the public application entry point.
  - Non-gateway service names must match the service module names.
  - Platform bootstrap must not contain business schema migrations.

## Runtime Configuration Variable

- **Represents**: A value injected into containers through Compose and later mapped to Kubernetes ConfigMaps or Secrets.
- **Fields**:
  - `name`: Environment variable name.
  - `classification`: `config` or `secret`.
  - `localSource`: Compose defaults or ignored `.env` file.
  - `futureKubernetesSource`: ConfigMap or Secret.
- **Validation rules**:
  - Secret values must not be committed except as placeholder examples.
  - Service-to-service addresses must use stable service names rather than host-local ports.

## Local Platform Service

- **Represents**: A root-owned local dependency container.
- **Fields**:
  - `name`: `postgres`, `redis`, or `kafka`.
  - `image`: Versioned container image.
  - `healthcheck`: Local readiness signal where practical.
  - `volume`: Local persistent storage volume when needed.
- **Validation rules**:
  - Must not centralize service-owned business schemas.
  - Must be replaceable by managed infrastructure or Kubernetes resources later.

## Kubernetes Migration Mapping

- **Represents**: Documentation mapping local Compose conventions to future Kubernetes resources.
- **Fields**:
  - `composeConcept`: Service, env var, health endpoint, image, profile, volume.
  - `kubernetesConcept`: Deployment, Service, ConfigMap, Secret, probe, Job, PersistentVolumeClaim, overlay.
  - `migrationNote`: Constraint or future action.
- **Validation rules**:
  - Must keep Kubernetes Service/DNS as the discovery model.
  - Must keep public ingress through `api-gateway`.
  - Must not introduce a second production deployment source of truth.
