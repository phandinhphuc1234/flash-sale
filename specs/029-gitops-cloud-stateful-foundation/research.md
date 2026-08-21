# Research: Cloud Stateful Service Foundation

## Decision 1 — Run a single in-cluster instance of each dependency

- **Decision**: PostgreSQL, Redis, Kafka, and Schema Registry run inside the existing EKS cluster,
  one replica each.
- **Reason**: This matches the internship/demo objective and keeps the topology visible in Kubernetes.
  The three-node cluster is not being presented as a highly available production platform.
- **Rejected alternatives**: RDS/ElastiCache/MSK would improve operations but add cost, IAM/network
  integration, and a separate lifecycle that is not needed for this learning milestone. Multiple
  replicas would require HA semantics, quorum, backup, and failure testing that are out of scope.

## Decision 2 — One PostgreSQL instance with isolated logical databases

- **Decision**: Use one PostgreSQL StatefulSet and create one logical database per service.
- **Reason**: The local Compose topology already uses this pattern and the service-owned migration
  boundary remains intact. Infrastructure only creates empty databases.
- **Rejected alternative**: One PostgreSQL StatefulSet per service would multiply PVCs and resource
  use without improving the demo's service ownership guarantee.

## Decision 3 — Use internal DNS names matching Compose

- **Decision**: Use `postgres`, `redis`, `kafka`, and `schema-registry` as the canonical cloud Service
  names and endpoints.
- **Reason**: Existing service configuration and Compose defaults already use these names, reducing
  configuration drift in Phase 15.

## Decision 4 — Defer Secret provisioning

- **Decision**: Stateful manifests reference `flash-sale-secrets`, but this feature never creates a
  value-bearing Secret.
- **Reason**: Secret ownership and `.env` handling need a dedicated, auditable phase. Kustomize can
  still render and dry-run resources before the Secret exists.

## Decision 5 — No Kafka UI in cloud

- **Decision**: Keep Kafka UI local-only under the existing `tools` Compose profile.
- **Reason**: It is not required for application correctness and would consume another cloud Pod and
  expose operational UI surface without benefit to the GitOps flow.
