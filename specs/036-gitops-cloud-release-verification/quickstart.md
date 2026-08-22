# Quickstart: Phase 21 Cloud Release Verification

Run from the repository root with PowerShell 7. The command is read-only except for the temporary
localhost port-forward created and cleaned by the existing Gateway smoke helper.

## Prerequisites

- AWS CLI profile `flash-sale-terraform` can describe ECR images.
- `kubectl` points to `flash-sale-dev`.
- `flash-sale-cloud` is managed by Argo CD.
- Phase 20 topic/schema provisioning is complete.

## Static checks

```powershell
pwsh -NoLogo -NoProfile -Command '$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile(".\infra\scripts\gitops\phase21-cloud-release-verify.ps1",[ref]$tokens,[ref]$errors)|Out-Null;if($errors.Count -gt 0){$errors|% Message;exit 1};"AST parse PASS"'
kubectl kustomize infra/k8s/overlays/cloud | Out-Null
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

## Live verification

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase21-cloud-release-verify.ps1
```

Expected summary:

```text
Argo flash-sale-cloud: Synced|Healthy
Release artifacts: 8/8 digest matches
Payment flags: 7/7 disabled
Gateway smoke: readiness=200 catalog=200 admin=401
Phase 21 cloud release verification: PASS
```

No `-Apply` switch exists for this phase. A failed check must be investigated and rerun; it never
changes the cluster or registry.
