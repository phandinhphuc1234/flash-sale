# Phase 15 validation evidence

**Date:** 2026-08-21  
**Branch:** `codex/gitops-phase15-cloud-secrets-config`

## Checks

| Check | Command/scope | Result |
|---|---|---|
| Cloud render | `kubectl kustomize infra/k8s/overlays/cloud` | PASS; eight app ConfigMaps, platform resources, and per-service patches render |
| Client dry-run | `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS; no live resource changed |
| Manual input validation | `powershell -NoProfile -ExecutionPolicy Bypass -File infra/scripts/gitops/phase15-secrets.ps1` | PASS; required key names and both JWT file paths present; validation-only mode |
| Stripe classification | same script with `-EnableStripe` | PASS; opt-in path validates without applying; Phase 15 default remains disabled |
| Missing-input guard | same script with a nonexistent `-SourceEnvFile` | PASS; non-zero exit and no value output |
| JWT file-argument regression check | `kubectl create secret generic auth-jwt --dry-run=client` with both PEM file arguments | PASS; both file arguments are passed as single PowerShell arguments; no live resource changed |
| Gateway DNS assertion | rendered cloud overlay | PASS; `FLASHSALE_SERVICE_URL=http://flash-sale-service:8080` |
| Legacy Secret assertion | rendered cloud overlay | PASS; no Deployment or platform resource references `flash-sale-secrets` |
| Payment safety | rendered cloud overlay | PASS; `STRIPE_ENABLED`, `PAYMENT_ACCEPTANCE_ENABLED`, and `PAYMENT_CHECKOUT_ENABLED` are false |
| Formatting | `git diff --check` | PASS; only normal Git line-ending warnings were reported |

## Safety evidence

- No `-Apply` command was run by the agent.
- No `.env`, PEM body, Secret manifest, or credential value was added to Git.
- The provisioning script prints key/resource names and file names only.
- Cloud ConfigMaps contain endpoints, metadata, topics, and disabled feature flags; sensitive values
  are supplied later by the operator-controlled script.

## Deferred work

- Do not run live Secret provisioning until the operator reviews `infra/CONFIGURATION.md` and confirms
  the ignored local `.env` and JWT directory are complete.
- Do not enable Stripe or run migrations in Phase 15. Those require their own approved phases.
