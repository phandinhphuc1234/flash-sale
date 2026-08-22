# Research: Full-Stack Argo CD Ownership

## Decision 1 — One cloud Application

**Decision**: Create flash-sale-cloud targeting infra/k8s/overlays/cloud.

**Rationale**: The canonical cloud overlay already contains the complete eight-service and platform
topology. One owner prevents the historical Product pilot from reverting cloud configuration.

**Alternatives considered**:

- Keep dev-pilot as the full-stack name: rejected because the name contradicts the environment
  contract and obscures ownership.
- Create one Application per service: deferred because this internship environment has one replica
  per service and does not yet need independent Argo projects or sync waves.

## Decision 2 — Guarded ownership handoff

**Decision**: Suspend pilot auto-sync, reconcile the cloud owner, and remove the pilot Application
object only after the cloud owner is Synced and Healthy.

**Rationale**: Both overlays contain Product resources with different runtime wiring and image
history. Simultaneous self-healing would cause an ownership fight.

**Alternatives considered**:

- Apply both active Applications: rejected because two controllers would continuously overwrite
  Product desired state.
- Delete the pilot first: rejected because a failed cloud reconciliation would leave no active
  desired-state owner.

## Decision 3 — No cascading delete and no prune

**Decision**: Refuse pilot removal when a resource-deletion finalizer is present and keep prune
disabled on the cloud Application.

**Rationale**: Phase 19 changes controller ownership, not workload/data lifecycle. Pilot-only
PostgreSQL and PVC cleanup needs a separate explicit phase.

## Decision 4 — Preserve Product image identity

**Decision**: Copy the approved Product pilot immutable tag into the cloud overlay before cutover.

**Rationale**: Ownership migration must not silently downgrade application code to the older
initial tag.

## Decision 5 — Migration Jobs stay operator-gated

**Decision**: Keep infra/k8s/overlays/cloud-migrations outside Argo application reconciliation.

**Rationale**: ADR 0021 requires migrations to run once through an explicit operator gate, not on
every application sync.
