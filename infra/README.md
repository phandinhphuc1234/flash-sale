# Shared Infrastructure

`infra/` is the root ownership boundary for repository-wide platform and environment assets. The
repository now includes a local Docker Compose baseline under `infra/docker/`. Kubernetes, Helm,
and monitoring server infrastructure remain planned future work.

## Directory responsibilities

| Directory | Owns | Does not own |
|-----------|------|--------------|
| `docker/` | Shared local backing-service orchestration and bootstrap assets, including Kafka and Schema Registry | Service runtime configuration or business-schema migrations |
| `k8s/` | First-party Kubernetes bases and environment overlays | Application code or a second service-discovery registry |
| `helm/` | Approved charts and third-party values | Resources already independently owned by Kustomize |
| `monitoring/` | Prometheus scrape infrastructure, Grafana resources, and alert rules | Service-side Actuator dependencies or endpoint configuration |

Service-owned source, dependencies, `application*.yml`, tests, and database migrations remain under
`services/<service>/`. A service-specific image build recipe may stay with its service when needed
for independent image builds.

Infrastructure can provision isolated databases and shared platform components, but it must not
centralize business schemas or enable cross-service database access. The local PostgreSQL bootstrap
under `infra/docker/postgres/init/` creates logical developer databases only; business migrations
remain service-owned.

See [ADR 0001](../docs/adr/0001-root-infrastructure-ownership.md) for the governing decision.

## Supported environments

The project intentionally has only two deployment environments:

- `local`: Docker Compose under `infra/docker/`.
- `cloud`: AWS EKS/Kustomize under `infra/k8s/overlays/cloud/`, reconciled by Argo CD in the later
  GitOps phases.

`dev-pilot` is historical Product-pilot evidence, not a third environment. See
[ENVIRONMENTS.md](ENVIRONMENTS.md) and validate the contract with
`infra/scripts/gitops/phase13-environment-contract.ps1`.

## Prometheus boundary

Each Spring Boot 3.x service exposes `/actuator/prometheus` through Actuator auto-configuration, a
runtime `micrometer-registry-prometheus` dependency, and declarative YAML. That service-side setup
stays with the service. Future Prometheus deployment and scrape configuration belongs in
`monitoring/`.

## Validation for future changes

- Record new production dependencies and infrastructure choices in the active feature plan.
- Document exact files and validation commands in `tasks.md`.
- Validate Docker Compose changes with `docker compose ... config`.
- Run `kubectl apply --dry-run=client -k <overlay>` for each changed Kubernetes overlay.
- Add an ADR for an architectural boundary or ownership exception.
