# Validation Evidence: Product Pilot GitOps Rollback Rehearsal

**Feature**: `027-gitops-rollback-rehearsal`
**Scope**: `dev-pilot` Product Deployment only

| Check | Evidence | Result |
|---|---|---|
| Preflight | Argo `dev-pilot` was `Synced`/`Healthy`; current image was `pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72` | PASS |
| Previous image availability | ECR tag `pilot-f5fa7cb`, digest `sha256:903bb68558819e51aeaa425b91f4cb8ca2a5b78db5d23c60130990483b7ac223` | PASS |
| Rollback commit | `80187fd` reverted merge `50d51eb`; only `infra/k8s/overlays/dev-pilot/kustomization.yaml` changed | PASS |
| Rollback validation | `git diff --check`; `kubectl kustomize infra/k8s/overlays/dev-pilot` | PASS |
| Rollback PR | [PR #48](https://github.com/phandinhphuc1234/flash-sale/pull/48), merged as `1e6071107d031830a425b691dd92880f3ddda5ec` | PASS |
| Rollback CI | [Maven Verify run](https://github.com/phandinhphuc1234/flash-sale/actions/runs/32469987321) | PASS |
| Rollback reconciliation | Argo history revision `1e6071107d031830a425b691dd92880f3ddda5ec`; Product image `pilot-f5fa7cb`; rollout successful | PASS |
| Restoration commit | `80c9bed` reverted rollback merge `1e607110`; only the same Kustomize file changed | PASS |
| Restoration PR | [PR #49](https://github.com/phandinhphuc1234/flash-sale/pull/49), merged as `7ec4d0d419d8e6467e944c071f5677c590017466` | PASS |
| Restoration CI | [Maven Verify run](https://github.com/phandinhphuc1234/flash-sale/actions/runs/32472609212) | PASS |
| Final reconciliation | Argo revision `7ec4d0d419d8e6467e944c071f5677c590017466`, `Synced`/`Healthy`; Product image restored to `pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`; rollout successful | PASS |

## Safety evidence

- No `.env`, Kubernetes Secret, application source, or ECR image was changed or deleted.
- Both state changes were reviewed through protected-branch PRs.
- No direct `kubectl apply` or `kubectl set image` was used; Argo CD reconciled both Git revisions.
- The Product PostgreSQL StatefulSet was not changed by either PR.
