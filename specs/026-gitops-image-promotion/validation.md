# Validation Evidence: Product Pilot Image Promotion

**Feature**: `026-gitops-image-promotion`
**Branch**: `codex/gitops-phase11-image-promotion`

| Check | Command/evidence | Result |
|---|---|---|
| Spec/plan/tasks | Active pointer and approved artifacts | PASS — feature pointer is `specs/026-gitops-image-promotion` |
| Workflow YAML | PyYAML parse of `.github/workflows/product-pilot-delivery.yml` | PASS |
| AWS role preflight | `aws iam get-role/list-attached-role-policies` (read-only) | PASS — `github-ci-role`, ECR PowerUser attached |
| PowerShell parser | `phase11-product-delivery.ps1` | PASS |
| Local default mode | `phase11-product-delivery.ps1` | PASS — local image built, no ECR push or overlay write |
| Product Maven verify | `./mvnw -pl services/product-service -am verify` | PASS — common-web 9 tests; Product 35 tests; 0 failures |
| Kustomize render | `kubectl kustomize infra/k8s/overlays/dev-pilot` | PASS |
| Hosted delivery | GitHub Actions `Product Pilot Delivery` run [32465545956](https://github.com/phandinhphuc1234/flash-sale/actions/runs/32465545956) | PASS — push on merge commit `11cdf76ace24e6402eae0dbc1082d077766e3c72`; Maven, OIDC, ECR push, promotion branch, and PR steps succeeded |
| ECR image | `flash-sale/product-service:pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72` | PASS — digest `sha256:121c8cd3875fecae1217821cf71bb63bf00409d4b19e2f3aa9eb04427266305e` |
| Promotion PR | [PR #46](https://github.com/phandinhphuc1234/flash-sale/pull/46) targeting `develop` | PASS — merged as `50d51ebca7d65907f4fd5e234cb25d9286a3de83`; diff changed only `infra/k8s/overlays/dev-pilot/kustomization.yaml` |
| Argo reconciliation | `kubectl -n argocd get application dev-pilot` | PASS — revision `50d51ebca7d65907f4fd5e234cb25d9286a3de83`, `Synced`, `Healthy` |
| EKS rollout | `kubectl -n flash-sale rollout status deployment/product-service --timeout=180s` | PASS — new Product Pod `1/1 Running` with the promoted immutable image tag |

## Safety evidence

- No AWS access key, GitHub token, registry credential, database password, or Kubernetes Secret value
  is intended to be stored in this feature.
- The workflow does not run `kubectl apply` or push directly to protected `develop`.
- Local default run derived `pilot-aa4de263e5156c0bcbc0ee54f92745e16d37c24d` and built
  `flash-sale/product-service` without remote mutation.

## Hosted evidence captured

- Required `Maven Verify` passed for the automation branch before the promotion PR was merged.
- Post-merge CI on `develop` also passed in run `32466870193`.
- Argo CD reconciled the merged desired state automatically; no manual `kubectl apply` was used.
