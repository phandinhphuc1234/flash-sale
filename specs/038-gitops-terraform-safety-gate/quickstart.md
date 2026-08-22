# Phase 23 Quickstart

## Prerequisites

- PowerShell 7, Terraform `>=1.10,<2.0`, AWS CLI, and the initialized `infra/terraform` directory.
- AWS profile `flash-sale-terraform` can read the existing remote state.
- Know the current public IPv4 address used for EKS API access. Do not commit it.

## Run

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c codex/gitops-phase23-terraform-gate

$env:AWS_PROFILE = "flash-sale-terraform"

pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase23-terraform-gate.ps1 -AutoDetectPublicIp
```

The helper never runs `terraform apply`. A clean plan ends with `Plan: NO_CHANGES`. A plan exit code
2 ends with `Plan: CHANGES_REVIEW_REQUIRED` and must be reviewed before any later apply decision.
If auto-detection is unavailable, set `TF_VAR_cluster_endpoint_public_access_cidrs` manually to a
list such as `["your-public-ip/32"]` and omit the switch.

## Direct checks performed

```powershell
terraform fmt -check -diff
terraform validate
terraform plan -input=false -detailed-exitcode -no-color
```
