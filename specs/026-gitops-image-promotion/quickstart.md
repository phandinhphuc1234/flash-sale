# Quickstart: Phase 11 Product Image Promotion

## Preconditions

- Phase 10 is merged and `dev-pilot` currently reports `Synced`/`Healthy`.
- ECR repository `flash-sale/product-service` exists and is immutable.
- `github-ci-role` trusts this repository's GitHub OIDC subject and currently has the AWS-managed
  `AmazonEC2ContainerRegistryPowerUser` policy. It can push the pilot image, but this is broader than
  the final least-privilege posture and is recorded for a later hardening phase.
- GitHub Actions repository settings allow `contents: write` and `pull-requests: write` for the
  workflow token.
- The private GitHub repository credential Secret already exists in `argocd`.

## Local validation-only rehearsal

```powershell
.\infra\scripts\gitops\phase11-product-delivery.ps1
```

This mode does not push to ECR or write the overlay.

## Local explicit publish and overlay update

```powershell
.\infra\scripts\gitops\phase11-product-delivery.ps1 -Push -UpdateOverlay
kubectl kustomize infra/k8s/overlays/dev-pilot
git diff -- infra/k8s/overlays/dev-pilot/kustomization.yaml
```

The helper does not commit or push Git changes. Review and commit the diff manually if this mode is
used; the hosted workflow normally creates the pull request for you.

## Hosted workflow evidence

1. Merge a small Product Service change into `develop`.
2. Open Actions → `Product Pilot Delivery` and confirm Maven verification, OIDC, ECR push, and PR.
3. Review the automation PR; do not merge it until the required checks pass.
4. After merging the promotion PR, run:

```powershell
kubectl -n argocd get application dev-pilot
kubectl -n flash-sale get pods
```

Expected: `Synced`, `Healthy`, and the Product Service Pod running the new immutable tag.
