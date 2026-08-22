# Implementation Plan: Terraform Safety and Drift Gate

**Branch**: `codex/gitops-phase23-terraform-gate` | **Date**: 2026-08-22 | **Spec**: [spec.md](spec.md)

## Summary

Add a read-only PowerShell 7 wrapper around the existing Terraform root module. It validates the
operator CIDR input, AWS identity, formatting, configuration, and detailed plan with bounded process
execution. It never runs apply or writes tfvars/state.

## Technical Context

**Language/Version**: PowerShell 7+, Terraform >=1.10,<2.0, AWS CLI

**Primary Dependencies**: Existing Terraform root module, AWS profile `flash-sale-terraform`, S3 backend

**Storage**: Existing remote Terraform state is refreshed by plan; no state output is printed or changed

**Testing**: PowerShell AST parse, Terraform fmt/validate/plan, AWS identity check, negative CIDR test,
and `git diff --check`

**Target Platform**: Windows operator workstation and the existing AWS account/region

**Project Type**: Repository-level GitOps operator script and evidence

**Performance Goals**: Bounded execution within 900 seconds

**Constraints**: Read-only; explicit CIDR input; no apply; no credentials/state output; no `.env` access

**Scale/Scope**: One Terraform root module under `infra/terraform`

## Constitution Check

- Specification traceability: PASS; FR-001–FR-006 map to script checks.
- Service ownership: PASS; no service source or persistence changes.
- Communication: PASS; no HTTP/Kafka contract changes.
- Data and messaging: PASS; no runtime state mutation.
- Root infrastructure ownership: PASS; script is under `infra/scripts/gitops`.
- Observability: PASS; bounded operator output and evidence.
- Contracts and dependencies: PASS; no production dependency added.
- Validation: PASS; Terraform and PowerShell checks are applicable; Maven/Kubernetes runtime checks are not.

## Design

1. Require PowerShell 7 and resolve `infra/terraform` from the script path.
2. Require `TF_VAR_cluster_endpoint_public_access_cidrs` as a Terraform list expression, or use
   a bounded `api.ipify.org` request only with explicit `-AutoDetectPublicIp`; validate IPv4 CIDRs
   and reject `0.0.0.0/0` without printing the value.
3. Run bounded `aws sts get-caller-identity`, `terraform fmt -check`, `terraform validate`, and
   `terraform plan -input=false -detailed-exitcode -no-color`.
4. Treat plan exit `0` as clean, exit `2` as review-required, and exit `1` or timeout as failure.
5. Never call apply, write tfvars, write state, or print plan/state/credential contents.

## Project Structure

```text
infra/scripts/gitops/phase23-terraform-gate.ps1
specs/038-gitops-terraform-safety-gate/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
├── validation.md
└── checklists/requirements.md
```

**Structure Decision**: Keep Terraform ownership in `infra/terraform`; the new operator helper and
evidence remain in root GitOps/spec directories.

## Complexity Tracking

No constitution violations.
