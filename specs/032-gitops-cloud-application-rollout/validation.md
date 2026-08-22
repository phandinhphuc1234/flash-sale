# Phase 17 Validation Evidence

Date: 2026-08-22
Branch: `codex/gitops-phase17-cloud-application-rollout`

## Automated validation

| Check | Result |
|---|---|
| `./mvnw -pl services/payment-service -am verify -DskipTests=false` | PASS — Payment module: 147 tests, 0 failures, 6 intentional skips; reactor build successful |
| `kubectl kustomize infra/k8s/overlays/cloud` | PASS |
| `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS |
| `infra/scripts/gitops/phase17-application-rollout.ps1` | PASS — eight application Deployments render; no Secret values read or printed |
| ECR image `flash-sale/payment-service:phase17-5b4fc91` | PUSHED — digest `sha256:dfa4e947dfbde507d754598766c4adde519c32bc22ea5517a5e11a9000a0309d` |

## Rollout evidence

The live rollout is executed after this desired-state tag is pushed. The operator must run:

```powershell
.\infra\scripts\gitops\phase17-application-rollout.ps1 -Apply -TimeoutSeconds 600
```

Expected result: all eight application Deployments reach one available replica, Payment liveness
and readiness return a non-401 health response, and unauthenticated Payment business APIs remain
protected while acceptance is disabled.

## Safety notes

- Payment acceptance, Checkout, and Stripe remain disabled in this phase.
- The image tag is immutable and is the only application image changed by this phase.
- Secret values are not read, rendered, logged, or committed.
