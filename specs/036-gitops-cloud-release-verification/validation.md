# Phase 21 Validation Evidence: Cloud Release Artifact Verification

**Date**: 2026-08-22

**Cluster**: `flash-sale-dev` (`ap-southeast-2`)

**Feature revision**: `da71f670ea59384d41c7b701cccdc6e47c1b6c1b`

## Static validation

| Command/check | Scope | Result |
|---|---|---|
| PowerShell AST parser | Phase 21 verifier and Phase 18 smoke helper | PASS, 2/2 scripts |
| `kubectl kustomize infra/k8s/overlays/cloud` | Cloud desired state render | PASS |
| `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | Cloud desired state | PASS, all resources renderable |
| `git diff --check` | Phase 21 changes | PASS |

## Live verification

Command:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase21-cloud-release-verify.ps1
```

Observed:

- Argo `flash-sale-cloud`: `Synced`, `Healthy`, target `develop`, revision
  `da71f670ea59384d41c7b701cccdc6e47c1b6c1b` (post-merge verification).
- Eight Deployments were available at `1/1`, and every Pod image ID matched the ECR manifest digest:

| Service | Tag | Manifest digest |
|---|---|---|
| `api-gateway` | `initial` | `sha256:241cb9cf41c26783d6ca740e699efadce3a00c5ebd6dfd07d09e5c7967b56c65` |
| `authentication-service` | `initial` | `sha256:240d0bdb1d083e5b335f780583408397c1086650fcf7c2d3f0cb4bf7ae8aa508` |
| `product-service` | `pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72` | `sha256:121c8cd3875fecae1217821cf71bb63bf00409d4b19e2f3aa9eb04427266305e` |
| `campaign-service` | `initial` | `sha256:fed083949e1e718035efa58e9cbfdc0e4ab05e1cbb3680916481b89e1c280828` |
| `flash-sale-service` | `initial` | `sha256:4c889889cde54a996017ad3ccedf7c6df45d0bfa678b0cf2b547f10aac51ce9c` |
| `inventory-service` | `initial` | `sha256:180800934196f537c9e82e6b601678ca02cc6b8227406ff044927eb7854c726b` |
| `order-service` | `initial` | `sha256:c616c61899b8ee740ca265e8e80bfdb061e151be51a8146e6beebcf6f925951b` |
| `payment-service` | `phase17-5b4fc91` | `sha256:dfa4e947dfbde507d754598766c4adde519c32bc22ea5517a5e11a9000a0309d` |

- Payment runtime flags: `7/7 disabled`.
- Gateway smoke: readiness `200`, catalog `200`, anonymous admin `401`.
- No Kubernetes Secret values were read or printed.
- No Kubernetes, Argo, ECR, PostgreSQL, Redis, or Kafka state was changed by Phase 21.

## Outcome

```text
Phase 21 cloud release verification: PASS
```
