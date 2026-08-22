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

Command executed:

```powershell
.\infra\scripts\gitops\phase17-application-rollout.ps1 -Apply -TimeoutSeconds 600
```

Result: PASS — all eight application Deployments reached `1/1` available. The Payment pod ran
image `payment-service:phase17-5b4fc91`; direct in-pod probes returned `200` for both
`/actuator/health/liveness` and `/actuator/health/readiness`. An unauthenticated request to
`/api/v1/payments` returned `403`, confirming the disabled Payment business API remains protected.

## Safety notes

- Payment acceptance, Checkout, and Stripe remain disabled in this phase.
- The image tag is immutable and is the only application image changed by this phase.
- Secret values are not read, rendered, logged, or committed.
