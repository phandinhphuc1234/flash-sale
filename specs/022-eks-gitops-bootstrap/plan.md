# Implementation Plan: EKS GitOps Bootstrap Hardening

**Branch**: `codex/eks-gitops-bootstrap` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Status**: Approved by project owner on 2026-08-20 for implementation and read-only validation;
`terraform apply` is excluded.

**Input**: Approved feature specification from `specs/022-eks-gitops-bootstrap/spec.md`.

## Summary

Harden the existing development Terraform root module before the first EKS apply. Preserve the
already-applied S3 state backend, two-AZ VPC, single NAT gateway, eight ECR repositories, and
three-node managed node-group sizing. Move the new cluster to EKS `1.36`, require an explicit safe
operator CIDR for the public API endpoint while retaining private access, use immutable GitOps image
tags, add stable module pins, default ownership tags, inputs and outputs, and leave a clean
format/validate/refreshed-plan evidence trail. No AWS mutation is performed.

## Technical Context

**Language/Version**: Terraform CLI `1.15.8`; HCL root module

**Primary Dependencies**: AWS provider `5.100.0`; `terraform-aws-modules/vpc/aws` `5.21.0`;
`terraform-aws-modules/eks/aws` `20.37.2`; transitive TLS, time, cloud-init, null, and KMS modules

**Storage**: Existing encrypted/versioned/private S3 backend with native S3 lockfile; no workload
storage in this feature

**Testing**: `terraform fmt -check -recursive`, `terraform validate -no-color`, refreshed
`terraform plan`, Git status/diff checks, and AWS read-only identity/version/offering inspection

**Target Platform**: AWS `ap-southeast-2`; EKS `1.36`; EC2 managed node group spanning private
subnets in `ap-southeast-2a` and `ap-southeast-2b`

**Project Type**: Repository-root infrastructure module in a Java 21 Maven microservice monorepo

**Performance Goals**: Preserve the accepted development baseline of three `m7i-flex.large`
workers; workload performance is deferred until Kubernetes resources exist

**Constraints**: No apply/state mutation; no committed AWS credential/profile or administrator IP;
no Kubernetes/Argo/data workload; no destroy/replacement plan; public EKS endpoint must exclude
`0.0.0.0/0`

**Scale/Scope**: One development cluster, one managed node group, eight ECR repositories, two
Availability Zones, two public and two private subnets, one NAT gateway

## Constitution Check

*GATE: Passed before research and passed again after design.*

| Gate | Result | Design evidence |
|---|---|---|
| Specification traceability | PASS | The approved spec bounds work to pre-apply Terraform hardening; all changes map to FR-001–FR-011. |
| Service ownership | PASS | No service source, database, migration, JPA type, or business model is changed. |
| Communication | PASS | No HTTP/Kafka contract or discovery behavior changes; future public ingress remains through API Gateway. |
| Data and messaging | PASS | No PostgreSQL, Redis, Kafka, stock, idempotency, outbox, or reconciliation behavior changes. |
| Root infrastructure ownership | PASS | Shared AWS configuration remains under `infra/terraform`; service-owned assets are untouched. |
| Observability | PASS | No application observability change; EKS module control-plane logging remains enabled by its reviewed defaults. |
| Contracts and dependencies | PASS | No new provider/module dependency; reviewed provider and module versions are pinned/locked. |
| Validation | PASS | Terraform format, validate, refreshed plan, Git hygiene, and diff evidence apply; Maven/load/contract/Kubernetes checks are inapplicable and explicitly omitted. |
| Architecture decisions | PASS | ADR 0001 already establishes root infrastructure ownership; no service/deployment boundary is changed. |

### Post-design re-check

Research confirms EKS `1.36` is the current regional default under standard support, the selected
instance type is offered in both configured Availability Zones, and the existing subnet role tags
match EKS load-balancer discovery guidance. S3 versioning, encryption, public-access blocking, and
native lock configuration already satisfy the backend baseline. EBS CSI and stateful workloads are
deliberately deferred, so no new IAM/provider dependency or Kubernetes resource is hidden in this
group.

## Design Decisions

### Supported cluster version

Use EKS `1.36` for the new cluster. `1.33` is in extended support in the target region and would add
avoidable lifecycle/cost debt before the first cluster exists.

### Endpoint access

Keep private endpoint access enabled and public access available for the operator laptop, but make
the public CIDR a required local input with validation that rejects empty lists and `0.0.0.0/0`.
Credentials, profiles, and the concrete CIDR remain outside Git.

The cluster uses a conventional managed node group. Disable the module's EKS Auto Mode custom-tag
permissions explicitly so the cluster role does not receive unused Auto Mode policy actions.

### Module and provider reproducibility

Pin the installed VPC and EKS module releases because Terraform lock files track providers but not
remote module selections. Keep the existing locked AWS provider family to avoid an unrelated v6
upgrade before cluster creation.

### ECR deployment identity

Use immutable tags because the release workflow will deploy images by Git SHA. Keep scan-on-push.
Lifecycle retention remains a later explicit decision.

### Stateful workload boundary

Do not install EBS CSI or create storage resources yet. The next infrastructure group will define
the IAM and storage class needed by any approved PostgreSQL, Redis, or Kafka StatefulSet.

## Project Structure

### Documentation

```text
specs/022-eks-gitops-bootstrap/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── validation.md
├── contracts/
│   └── terraform-bootstrap.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Infrastructure source

```text
infra/terraform/
├── .terraform.lock.hcl
├── .terraformignore
├── ecr.tf
├── eks.tf
├── outputs.tf
├── provider.tf
├── terraform.tfvars.example
├── variables.tf
└── vpc.tf

.gitignore
.specify/feature.json
```

**Structure Decision**: Continue using the single root Terraform module under `infra/terraform` for
the development AWS foundation. No service module or Kubernetes manifest is added.

## Validation Strategy

1. Verify `AWS_PROFILE=flash-sale-terraform` with STS.
2. Supply the administrator `/32` through `TF_VAR_cluster_endpoint_public_access_cidrs` for the
   current shell; never persist the concrete value in Git.
3. Run Terraform formatting and validation.
4. Run a refreshed plan without `-out` so no saved plan artifact is created.
5. Inspect actions and require zero destroy/replacement.
6. Confirm the plan selects EKS `1.36`, private endpoint access, the supplied CIDR, three on-demand
   `m7i-flex.large` desired workers, immutable ECR tags, and project/environment tags.
7. Run `git diff --check` and verify local Terraform cache/state/variable/plan files are excluded.

Maven, HTTP/Kafka contract, load, and Kubernetes dry-run checks are omitted because this group does
not change Java, transport contracts, runtime behavior, or Kubernetes manifests.

## Complexity Tracking

No constitutional departure or architectural exception is introduced.
