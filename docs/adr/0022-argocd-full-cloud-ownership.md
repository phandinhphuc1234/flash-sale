# ADR 0022: Argo CD Full-Cloud Desired-State Ownership

**Status**: Accepted
**Date**: 2026-08-22

## Context

The Product pilot proved Argo CD reconciliation and rollback, while later phases manually applied
the complete cloud topology. The live dev-pilot Application still self-heals overlapping Product
resources from infra/k8s/overlays/dev-pilot, so manual cloud changes to Product can be reverted.
The repository now has a validated canonical cloud overlay and needs one unambiguous owner.

## Decision

Create one flash-sale-cloud Argo CD Application targeting infra/k8s/overlays/cloud on develop.
During cutover, suspend dev-pilot auto-sync before activating the cloud owner. Remove the historical
Application object only after the cloud owner is Synced and Healthy and all eight application
Deployments are available.

The cloud Application uses self-heal with prune disabled. The transition refuses any cascading
resource finalizer and preserves the approved immutable Product image. Migration Jobs remain
outside application reconciliation under ADR 0021. Pilot-only PostgreSQL/PVC cleanup is deferred.

## Alternatives

- Keep two self-healing Applications: rejected because both own Product resources and can fight.
- Rename dev-pilot in place: rejected because Kubernetes objects cannot be renamed and retaining
  the pilot identity would obscure the canonical environment.
- One Application per service: deferred until independent sync policy or team ownership justifies
  the additional operational complexity.
- Enable prune during cutover: rejected because Phase 19 does not authorize deletion of pilot-only
  stateful resources.

## Consequences

Positive:

- Git on develop becomes the source of truth for the full cloud environment.
- Drift across all eight services and platform resources is self-healed.
- The historical pilot cannot silently revert Product cloud configuration.

Trade-offs:

- One Application couples infrastructure reconciliation status at the environment level.
- Pilot-only PostgreSQL and PVC resources remain until a later cleanup decision.
- Image promotion workflows must eventually target the cloud overlay instead of the pilot overlay.

## Rollback

If cloud reconciliation fails before cutover completes, delete only the finalizer-free
flash-sale-cloud Application object and reapply application-dev-pilot.yaml. Workloads and persistent
data remain intact because neither Application uses cascading deletion and pruning is disabled.
