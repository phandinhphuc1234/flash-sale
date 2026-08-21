# Validation Evidence: Local and Cloud Environment Foundation

**Date**: 2026-08-21
**Branch**: `codex/gitops-phase13-cloud-environment-foundation`

## Environment validator

| Command | Result |
|---|---|
| `infra/scripts/gitops/phase13-environment-contract.ps1 -Environment local` | PASS; resolved `infra/docker`; did not read or print secret values |
| `infra/scripts/gitops/phase13-environment-contract.ps1 -Environment cloud` | PASS; resolved `infra/k8s/overlays/cloud`; did not read or print secret values |
| Same script with `-Environment product` in a separate PowerShell process | PASS negative guard; exit code `1` and supported values were reported |

## Kubernetes validation

| Command | Result |
|---|---|
| `kubectl kustomize infra/k8s/overlays/cloud` | PASS; rendered namespace, ConfigMap, 8 Services, and 8 Deployments |
| `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS; no live resource changed |
| Cloud rendered image mapping | PASS; all 8 images use the ECR repository namespace and `initial` bootstrap tag |

## Safety and repository checks

| Check | Result |
|---|---|
| `git check-ignore --quiet -- infra/docker/.env` | PASS; local secret file is ignored |
| Value-leak review of changed files and validator output | PASS; no secret values, PEM contents, or provider credentials printed |
| `git diff --check` | PASS; no whitespace errors |

## Scope confirmation

- No Java source, database migration, HTTP contract, Kafka contract, Terraform resource, or live
  cloud resource changed.
- `dev-pilot` remains available as historical rollback evidence.
- The next feature must provision stateful cloud dependencies and operator-managed Secrets before a
  live full-stack apply.
