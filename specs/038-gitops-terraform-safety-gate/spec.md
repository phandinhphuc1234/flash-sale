# Feature Specification: Terraform Safety and Drift Gate

**Feature Branch**: `codex/gitops-phase23-terraform-gate`

**Created**: 2026-08-22

**Status**: Verified

**Input**: User request to continue the local-plus-cloud GitOps roadmap after the Phase 22 cloud guard.

**Roadmap alignment**: This feature is a Terraform safety gate supporting the canonical roadmap in
[`docs/deployment/gitops-roadmap-status.md`](../../docs/deployment/gitops-roadmap-status.md). It is
not the canonical roadmap's public Gateway phase.

## Problem and Scope

The EKS environment is already provisioned, but a later operator could run Terraform with a missing
CIDR variable or an unexpected plan and accidentally apply an unsafe change. Phase 23 adds a bounded,
read-only Terraform gate that validates formatting, configuration, AWS identity, and the current plan
before any apply is considered.

### In Scope

- Validate Terraform formatting and configuration in `infra/terraform`.
- Require an explicit `TF_VAR_cluster_endpoint_public_access_cidrs` value or an explicit
  `-AutoDetectPublicIp` opt-in, and reject the public `0.0.0.0/0` CIDR.
- Verify the configured AWS profile can identify the expected account/region without printing credentials.
- Run a detailed Terraform plan and classify no-change versus change-detected results.
- Keep all operations read-only; report plan changes for review and never apply them.

### Out of Scope

- `terraform apply`, destroy, import, state manipulation, or lock-file edits.
- Changing EKS, VPC, IAM, ECR, node groups, or the public endpoint CIDR.
- Reading `.env`, Kubernetes Secrets, JWT files, or AWS credentials.
- Production accounts or a second environment.

## User Scenarios & Testing

### User Story 1 - Review infrastructure changes safely (Priority: P1)

As the project operator, I want one command to validate Terraform and preview AWS changes, so that I
can review drift before any infrastructure mutation.

**Why this priority**: Terraform is the boundary that can change the EKS foundation; a safe plan gate
must precede any future apply or deployment work.

**Independent Test**: Run the Phase 23 verifier with the real public IPv4 CIDR; it passes formatting,
validation, AWS identity, and plan checks without invoking `terraform apply`.

**Acceptance Scenarios**:

1. **Given** a valid CIDR environment variable and AWS profile, **When** the verifier runs, **Then**
   formatting, validation, account/region, and plan checks complete within the time budget.
2. **Given** a missing/placeholder CIDR or `0.0.0.0/0`, **When** the verifier runs, **Then** it fails
   before Terraform plan and explains the safe input format.
3. **Given** Terraform detects changes, **When** the verifier runs, **Then** it reports changes
   requiring review and never applies them.

### Edge Cases

- Terraform plan exit code `2` indicates changes; it is not silently treated as an apply approval.
- AWS profile/account mismatch fails before plan.
- Terraform is missing, uninitialized, or outside the supported version range; the gate fails clearly.
- The command is run under Windows PowerShell 5.1; it fails with a PowerShell 7 requirement.

## Requirements

### Functional Requirements

- **FR-001**: The verifier MUST be read-only and MUST NOT call `terraform apply`, destroy, import, or state mutation commands.
- **FR-002**: The verifier MUST require a valid IPv4 CIDR list through
  `TF_VAR_cluster_endpoint_public_access_cidrs`, or derive the current IPv4 `/32` only when
  `-AutoDetectPublicIp` is explicitly supplied; it MUST reject `0.0.0.0/0`.
- **FR-003**: The verifier MUST run `terraform fmt -check`, `terraform validate`, and
  `terraform plan -input=false -detailed-exitcode` in `infra/terraform`.
- **FR-004**: The verifier MUST validate the AWS caller account and region using the configured profile
  without displaying credentials.
- **FR-005**: A plan exit code of `0` MUST be reported as no changes; exit code `2` MUST be reported as
  changes requiring review and MUST NOT be treated as approval to apply.
- **FR-006**: The verifier MUST use bounded child-process execution and record command scope/result in
  validation evidence.

### Non-Functional Requirements

- **NFR-SEC-001**: Output MUST not include AWS credential material, `.env` values, or Terraform state contents.
- **NFR-OPS-001**: The default execution MUST complete within 900 seconds or terminate child processes safely.

## Assumptions

- The operator has already initialized the Terraform working directory and has an AWS profile with the
  existing EKS permissions.
- The public endpoint CIDR is an operator input, not a committed tfvars value.
- A plan with changes is a review stop; this phase does not decide whether those changes are correct.

## Constitutional Constraints

- **Service ownership**: No service source, schema, or database changes.
- **External ingress**: No ingress or public endpoint change is made.
- **API/event contracts**: None.
- **Durable and hot-path data**: No PostgreSQL, Redis, or Kafka state is changed.
- **Messaging reliability**: None.
- **Root infrastructure ownership**: Script/evidence remain under `infra/scripts/gitops` and `specs/`.
- **Observability**: Operator diagnostics are bounded; no service metrics code changes.
- **Verification**: PowerShell parse, Terraform format/validate/plan, AWS identity, and diff checks apply.
- **Architecture decisions**: No ADR; no ownership or topology changes.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A valid plan completes with exit code 0 when no infrastructure changes are detected.
- **SC-002**: Missing, invalid, or public-wide CIDR input fails before plan execution.
- **SC-003**: A plan showing changes is clearly classified as review-required and never triggers apply.
- **SC-004**: The run emits no AWS credential, Secret, `.env`, or state values.

## Approval and History

- 2026-08-22 — Draft created from the local-plus-cloud GitOps roadmap.
- 2026-08-22 — Approved for implementation by proceeding to Phase 23 after Phase 22.
- 2026-08-22 — Verified with auto-detected public IPv4 and a no-change Terraform plan.
