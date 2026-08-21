# ADR 0019: Cloud Single-Node Stateful Foundation

**Status**: Accepted

**Date**: 2026-08-21

## Context

The project has only `local` and `cloud` environments. The local Docker Compose topology already
contains PostgreSQL, Redis, Kafka, and Schema Registry. The AWS EKS cluster is intended for an
internship/demo GitOps deployment with three `m7i-flex.large` nodes, not for a production HA claim.
The application services cannot be tested in the cloud until these dependencies have stable internal
DNS names and durable storage.

## Decision

For the cloud demo, deploy one in-cluster instance of each dependency under the `flash-sale` namespace:

- PostgreSQL: one StatefulSet, one `gp2` PVC, isolated logical database per service.
- Redis: one StatefulSet, one `gp2` PVC, append-only persistence and password authentication.
- Kafka: one KRaft broker/controller StatefulSet, one `gp2` PVC, replication factor one.
- Schema Registry: one Deployment backed by Kafka's `_schemas` topic.

All Services are internal ClusterIP or headless Services. The canonical names are `postgres`,
`redis`, `kafka`, and `schema-registry`. Credentials are supplied by an operator-managed Secret in
a later phase and never committed to Git.

Service-owned Liquibase migrations remain in the service modules. Infrastructure creates only empty
logical databases and does not query or own another service's schema.

## Alternatives Considered

### Managed RDS, ElastiCache, and MSK

Rejected for this phase because they add cost, IAM/network setup, and an additional lifecycle before
the GitOps application flow is demonstrated.

### One dependency instance per service

Rejected because it multiplies PVCs and resource consumption without changing the service ownership
boundary. Separate logical databases remain sufficient for the demo.

### High-availability replicas

Rejected because HA requires quorum, replication, backup/restore, failover, and capacity testing that
are intentionally outside the internship scope.

## Consequences

### Positive

- The complete cloud dependency graph is visible and reconciled as Kubernetes resources.
- Internal DNS names match the local Compose topology and simplify Phase 15 configuration.
- EBS PVCs preserve state across Pod restarts within the demo cluster.

### Negative and limitations

- A node, volume, or single process failure can interrupt the platform.
- Replication factor one is not suitable for production durability.
- Backups, upgrades, and credential rotation are operator responsibilities for this demo.

## Migration and Rollback

The resources are owned by `infra/k8s/overlays/cloud/platform/`. Removing that Kustomize resource
from the cloud overlay stops reconciliation, but PVC deletion requires an explicit operator action.
The existing `dev-pilot` Product stateful resources remain separate and are not modified by this ADR.
