# Data Model: Product Pilot GitOps Rollback Rehearsal

| Entity | Meaning | Key fields | State transitions |
|---|---|---|---|
| Promotion commit | Existing desired-state change | source SHA, new image tag, merge SHA | merged |
| Rollback PR | Reviewed reversal of the promotion | branch, base `develop`, changed path, check status | opened → checked → merged |
| Image reference | Immutable ECR artifact | repository, tag, digest | available → deployed |
| Argo revision | Git revision applied to EKS | revision, sync status, health status | observed → synced → healthy |
| Rollout observation | Kubernetes result after reconciliation | deployment, image, available replicas | pending → available |

## Invariants

- A rollback PR contains only the Product pilot Kustomize image-tag change.
- The target image tag exists before the PR is merged.
- No ECR image is deleted during the rehearsal.
- Argo revision and running image are recorded for both rollback and restoration.
