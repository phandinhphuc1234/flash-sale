# ADR 0007: Single-Replica EKS PostgreSQL for the Product Pilot

**Status**: Accepted
**Date**: 2026-08-21

## Context

The Phase 8 Product pilot proved the application image and database migrations on EKS, but the EBS
CSI add-on and PostgreSQL workload were created manually. The GitOps exercise needs a small,
reproducible stateful slice without introducing production HA or a managed database yet.

## Decision

Manage the existing EBS CSI integration through Terraform and describe one Product-owned PostgreSQL
StatefulSet through a dedicated Kustomize pilot overlay. The database uses one replica and an 8 GiB
EBS-backed PVC. PostgreSQL stores data under `/var/lib/postgresql/data/pgdata` so the mount root's
`lost+found` directory does not block initialization. Credentials remain external to Git.

## Alternatives considered

- RDS: deferred because it changes cost and operational scope.
- PostgreSQL operator/HA: rejected for the internship baseline.
- PVC deletion/recreation: rejected because it is destructive and not required.
- Deploying the full eight-service overlay: deferred until backing services and secrets are approved.

## Consequences

Positive:

- The pilot becomes reviewable and repeatable from Git.
- The existing PVC can be retained while Terraform adopts AWS storage resources.
- The stateful boundary is explicit and easy to replace later with RDS or HA PostgreSQL.

Trade-offs:

- The single replica is not production resilient.
- Operator-managed secrets remain a manual prerequisite until a secret-management feature is approved.
- Argo CD is still a later phase; this ADR does not authorize controller installation.

## Migration and rollback

- Import the existing IAM role and EKS add-on before Terraform apply.
- Stop if plan shows destroy/replacement.
- Roll back the Kustomize deployment by reverting the overlay/image commit; retain the PVC.
- Do not delete the PVC as part of rollback.
