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
| Hosted delivery | GitHub Actions run and ECR image digest | Pending operator run |
| Promotion PR | Automation PR targeting `develop` | Pending operator run |
| Argo reconciliation | `dev-pilot` = `Synced` + `Healthy` | Phase 10 baseline passed; Phase 11 pending |

## Safety evidence

- No AWS access key, GitHub token, registry credential, database password, or Kubernetes Secret value
  is intended to be stored in this feature.
- The workflow does not run `kubectl apply` or push directly to protected `develop`.
- Local default run derived `pilot-aa4de263e5156c0bcbc0ee54f92745e16d37c24d` and built
  `flash-sale/product-service` without remote mutation.
