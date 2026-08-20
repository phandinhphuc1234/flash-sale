# Tasks: EKS GitOps Bootstrap Hardening

**Input**: Approved design documents in `specs/022-eks-gitops-bootstrap/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/terraform-bootstrap.md`

**Tests**: Terraform formatting, validation, negative input validation, refreshed plan inspection,
and Git hygiene are mandatory. `terraform apply` is explicitly excluded.

## Phase 1: Setup

**Purpose**: Establish safe local artifact handling and the approved feature context.

- [X] T001 [P] Exclude Terraform cache, state, variable, crash, and saved-plan artifacts while
  retaining `.terraform.lock.hcl` in `.gitignore` and `infra/terraform/.terraformignore`
- [X] T002 [P] Add contributor-safe example inputs in
  `infra/terraform/terraform.tfvars.example`

---

## Phase 2: Foundational Configuration

**Purpose**: Make the root module reproducible before changing planned resources.

- [X] T003 Add supported Terraform/provider constraints, configurable region, and common ownership
  tags in `infra/terraform/provider.tf` and `infra/terraform/variables.tf`
- [X] T004 Pin the reviewed VPC module version and clean explanatory comments without changing the
  applied topology in `infra/terraform/vpc.tf`

**Checkpoint**: The root module has stable dependency selection and shared metadata.

---

## Phase 3: User Story 1 - Review a safe EKS creation plan (Priority: P1) MVP

**Goal**: Produce a supported, private-subnet EKS creation plan whose public API endpoint is
restricted to an explicitly supplied operator CIDR.

**Independent Test**: Missing or globally open CIDRs are rejected; a valid local `/32` allows a
plan with EKS `1.36`, three on-demand workers, and no destroy or replacement action.

- [X] T005 [US1] Add required IPv4 CIDR validation that rejects empty input and `0.0.0.0/0` in
  `infra/terraform/variables.tf`
- [X] T006 [US1] Pin EKS module `20.37.2`, select EKS `1.36`, configure private plus restricted
  public endpoint access, disable unused EKS Auto Mode policy permissions, and make the on-demand
  node capacity explicit in `infra/terraform/eks.tf`
- [X] T007 [US1] Verify expected failures for missing and globally open endpoint CIDRs without
  mutating AWS state

**Checkpoint**: The cluster creation plan cannot silently expose its API endpoint globally.

---

## Phase 4: User Story 2 - Reproduce infrastructure configuration from Git (Priority: P2)

**Goal**: Keep provider/module selections and GitOps image identities reviewable and expose stable
identifiers needed by later deployment automation.

**Independent Test**: Clean initialization uses committed selections, plans eight immutable ECR
repositories, and evaluates all documented outputs.

- [X] T008 [P] [US2] Make all eight ECR repositories immutable while preserving scan-on-push in
  `infra/terraform/ecr.tf`
- [X] T009 [P] [US2] Expose region, cluster name and endpoint, private subnet IDs, and repository
  URLs in `infra/terraform/outputs.tf`
- [X] T010 [US2] Remove the unused empty `infra/terraform/ec2.tf` placeholder and retain the
  generated provider selections in `infra/terraform/.terraform.lock.hcl`

**Checkpoint**: Later GitHub Actions and kubectl configuration can consume stable Terraform
outputs without inspecting local state manually.

---

## Phase 5: Validation and Evidence

**Purpose**: Prove the configuration is safe to review while leaving AWS unchanged.

- [X] T011 Run `terraform fmt -recursive`, `terraform fmt -check -recursive`, and
  `terraform validate -no-color` in `infra/terraform`
- [X] T012 Run a refreshed read-only plan with `AWS_PROFILE=flash-sale-terraform` and a temporary
  approved `/32`; verify no destroy/replacement and inspect the required EKS/ECR/tag changes
- [X] T013 Run `git diff --check`, inspect ignored local artifacts, and record commands, exit
  statuses, scope, and plan summary in `specs/022-eks-gitops-bootstrap/validation.md`

---

## Dependencies & Execution Order

- T001 and T002 can run in parallel.
- T003 and T004 depend only on setup and establish the shared root-module baseline.
- T005 and T006 implement User Story 1; T007 validates its negative paths.
- T008 and T009 can run in parallel after the foundation; T010 finishes repository cleanup.
- T011 through T013 run sequentially after all implementation tasks.
- No task authorizes `terraform apply`, state mutation, or Kubernetes workload creation.

## Implementation Strategy

1. Complete local safety and reproducibility tasks.
2. Implement and independently validate the EKS endpoint boundary.
3. Add GitOps ECR behavior and operator outputs.
4. Format, validate, refresh the plan, and record evidence.
5. Stop before apply and return the reviewed plan to the project owner.
