# Research: Local and Cloud Environment Foundation

## Decision 1 — Keep Docker Compose as the local source of truth

- **Decision**: `local` means the existing `infra/docker/` Compose topology and ignored
  `infra/docker/.env`.
- **Reason**: It is already the repository's repeatable local path for PostgreSQL, Redis, Kafka,
  Schema Registry, and service containers. Introducing a second local Kubernetes path would create
  two competing developer workflows.
- **Rejected alternative**: Make `infra/k8s/overlays/local` the default developer workflow. It would
  require local cluster storage and networking before the business services can be tested.

## Decision 2 — Make a `cloud` Kustomize overlay the canonical EKS target

- **Decision**: Add `infra/k8s/overlays/cloud/` for the complete eight-service topology. Argo CD
  will target this overlay in a later phase.
- **Reason**: The existing `dev` overlay is a full-stack precursor, while `dev-pilot` is intentionally
  Product-only. A named cloud overlay makes the promotion target explicit without deleting rollback
  history.
- **Rejected alternative**: Rename or delete `dev-pilot` now. That would erase useful rollback evidence
  and could invalidate the existing Argo Application before the new target is proven.

## Decision 3 — Defer credentials and stateful backing services

- **Decision**: This phase contains no secret values and no PostgreSQL, Redis, Kafka, or Schema
  Registry manifests. It only records the inventory needed by later phases.
- **Reason**: Secret provisioning and stateful placement have separate failure modes and need their
  own validation gates. Keeping them out of the environment foundation allows the cloud overlay to
  render in CI without credentials.
- **Rejected alternative**: Put a placeholder `Secret` in Git. Even fake-looking values tend to be
  copied into real deployments and blur the secret boundary.

## Decision 4 — Use one replica per application service initially

- **Decision**: The cloud overlay preserves the existing one-replica baseline; scaling and public
  ingress are later, explicit concerns.
- **Reason**: The current EKS cluster is a learning/demo environment and the user explicitly does not
  need production HA. This keeps resource pressure visible while the full flow is brought up.

## Compatibility Notes

- The base remains the shared resource owner under `infra/k8s/base/`.
- Existing `infra/k8s/overlays/dev/` and `dev-pilot/` remain available during migration.
- No Java module, image build, API contract, Kafka contract, migration, or Terraform resource changes
  are required by this phase.
