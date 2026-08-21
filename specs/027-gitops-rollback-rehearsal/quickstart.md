# Quickstart: Phase 12 Product Pilot Rollback

This runbook is for `dev-pilot` only. It never changes `.env`, Kubernetes Secret values, ECR image
contents, or application source.

## Preconditions

```powershell
git fetch origin --prune
kubectl -n argocd get application dev-pilot
kubectl -n flash-sale get deployment product-service
```

Expected: Argo `Synced`/`Healthy` and Product Deployment available.

The known-good rollback target is `pilot-f5fa7cb`; the current promoted tag is
`pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`.

## Rollback branch

```powershell
git switch -c codex/gitops-phase12-rollback origin/develop
git revert -m 1 --no-edit 50d51ebca7d65907f4fd5e234cb25d9286a3de83
git diff --check
git diff --stat HEAD^
kubectl kustomize infra/k8s/overlays/dev-pilot | Select-String product-service
```

The diff must contain only the desired Product image tag change. Push the branch, open a PR into
`develop`, and wait for `Maven Verify` before merging.

## Observe rollback

```powershell
kubectl -n argocd get application dev-pilot --watch
kubectl -n flash-sale rollout status deployment/product-service --timeout=180s
kubectl -n flash-sale get deployment product-service -o jsonpath="{.spec.template.spec.containers[0].image}{'\n'}"
```

Expected image: `.../product-service:pilot-f5fa7cb`.

## Restore forward state

Revert the rollback merge commit in a new branch, open a second PR into `develop`, wait for required
CI, merge, and repeat the Argo/rollout checks. Expected image returns to the current promoted tag.
