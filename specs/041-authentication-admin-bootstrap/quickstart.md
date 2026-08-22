# Quickstart: One-Time Cloud Admin Bootstrap

## Prerequisites

- Current `kubectl` context is the intended EKS cluster and namespace `flash-sale` exists.
- The Authentication image currently deployed in EKS contains this feature.
- `authentication-secrets`, `auth-jwt`, `flash-sale-runtime-config`, and
  `authentication-service-runtime-config` already exist.
- Do not put the admin password in `.env`, Git, a manifest, or chat.

## Validate without mutation

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-admin-bootstrap.ps1 `
  -AdminEmail "admin@example.test" `
  -AdminUsername "admin"
```

Expected: the current Authentication image and required runtime resources are reported; no Secret,
Job, or user row is changed.

## Apply once

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-admin-bootstrap.ps1 `
  -AdminEmail "admin@example.test" `
  -AdminUsername "admin" `
  -Apply
```

The script prompts for the password with `Read-Host -AsSecureString`, waits for the Job, prints only
sanitized status, deletes the temporary Secret, and removes the Job unless `-KeepJob` is supplied.

## Verify the handoff

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-internal-e2e.ps1 `
  -Run `
  -AdminLogin "admin"
```

The Phase 22 script prompts for the same password and verifies the four administrator authorities.
Record only the sanitized subject, Job outcome, Argo revision, and exit status in the feature
validation evidence.
