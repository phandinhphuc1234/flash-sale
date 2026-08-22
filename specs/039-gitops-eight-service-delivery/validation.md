# Validation: Eight-Service GitOps Image Delivery

**Feature**: `039-gitops-eight-service-delivery`

| Check | Command / evidence | Result |
|---|---|---|
| Workflow static validation | PyYAML parse + guard for jobs, eight mappings, OIDC, cloud path, and no kubectl command | PASS |
| Target mapping | Python inventory of `infra/k8s/overlays/cloud/kustomization.yaml` | PASS — eight image keys, including `flash-sale-service` |
| Maven verification | `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` | PASS — 13 modules, BUILD SUCCESS; Order reported 89 tests/8 intentional skips |
| Cloud Kustomize render | `kubectl kustomize infra/k8s/overlays/cloud` and `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS — client-side dry-run, no live mutation |
| Hosted service-local run | GitHub Actions run/PR reference | PENDING |
| Hosted shared-change run | GitHub Actions run/PR reference | PENDING |
| Phase 21 release evidence | `phase21-cloud-release-verify.ps1` | PENDING |

Secret values are intentionally excluded from this evidence file.
