# Validation: Full-Stack Argo CD Ownership

**Date**: 2026-08-22

**Environment**: AWS EKS `flash-sale-dev`

**Status**: PASS; live ownership transition and post-cutover Gateway smoke completed

## Static and client-side validation

| Scope | Command | Result |
| --- | --- | --- |
| PowerShell syntax | `System.Management.Automation.Language.Parser::ParseFile(...)` | PASS; zero parser errors |
| Argo desired state | `kubectl kustomize infra/k8s/argocd` | PASS; exactly one `flash-sale-cloud` Application, cloud path, prune false |
| Argo client dry-run | `kubectl apply --dry-run=client -k infra/k8s/argocd` | PASS |
| Cloud desired state | `kubectl kustomize infra/k8s/overlays/cloud` | PASS; approved immutable Product image rendered |
| Cloud client dry-run | `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS |
| Phase 19 preflight | `.\infra\scripts\gitops\phase19-argocd-cloud.ps1` | PASS; validation-only mode made no changes |

## Live prerequisite evidence

- `dev-pilot`: `Synced`, `Healthy`.
- Pilot automated policy: `selfHeal=true`, `prune=false`.
- `flash-sale-cloud`: absent before the approved transition, as expected.
- Eight application Deployments each report at least one available replica.
- Product image remains
  `090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`.
- Secret values were neither read nor printed.

## Pre-merge safety-gate test

Command:

    .\infra\scripts\gitops\phase19-argocd-cloud.ps1 -Apply

Expected and observed result: exit code 1 with
`Phase 19 is not present on origin/develop`. The guard ran before any mutation. A follow-up check
confirmed the pilot remained `Synced/Healthy`, its automated policy remained unchanged, the new
Application remained absent, and the Product image remained unchanged.

## Live ownership-transition evidence

The reviewed Phase 19 branch was merged into `develop` as merge commit `7831ce6`. Commands:

    git switch develop
    git pull --ff-only origin develop
    .\infra\scripts\gitops\phase19-argocd-cloud.ps1 -Apply -TimeoutSeconds 600
    .\infra\scripts\gitops\phase18-gateway-smoke.ps1 -Run

Observed results:

- `flash-sale-cloud` reached `Synced|Healthy` within the 600-second gate.
- `api-gateway`, `authentication-service`, `product-service`, `campaign-service`,
  `flash-sale-service`, `inventory-service`, `order-service`, and `payment-service` each completed
  rollout with one available replica.
- Exactly one Argo CD Application remains: `flash-sale-cloud`.
- `dev-pilot` was removed only after successful verification and without cascading resource
  deletion.
- Cloud ownership points to `infra/k8s/overlays/cloud`, with `selfHeal=true` and `prune=false`.
- Product preserved the approved immutable image
  `090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`.
- Gateway smoke passed with readiness `200`, public catalog `200`, and anonymous admin `401`.
- No migration rerun, Secret value read/print, workload deletion, or PVC deletion occurred.
