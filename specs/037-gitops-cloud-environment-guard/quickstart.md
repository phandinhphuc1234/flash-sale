# Phase 22 Quickstart

## Prerequisites

- PowerShell 7 (`pwsh`), AWS CLI profile `flash-sale-terraform`, and `kubectl` are available.
- The current kubeconfig context is the EKS cluster `flash-sale-dev`.
- Phase 15 Secret provisioning and Phase 21 release verification have passed.

## Run

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-cloud-guard.ps1
```

The command is read-only. It must print `Phase 22 cloud environment guard: PASS` and must never
print a Secret value.

## Static checks

```powershell
pwsh -NoLogo -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile('infra/scripts/gitops/phase22-cloud-guard.ps1',[ref]`$null,[ref]`$null) | Out-Null"
kubectl kustomize infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
git diff --check
```

## Expected guard sections

- EKS context and cloud overlay: PASS
- Argo ownership and policy: PASS
- ConfigMap/Secret boundaries: PASS
- Platform references and private Services: PASS
- Payment flags and Kafka safety: PASS
- Final read-only outcome: PASS
