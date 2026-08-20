# Validation Evidence: EKS GitOps Bootstrap Hardening

**Date**: 2026-08-20

**Scope**: Terraform source and read-only planning only. No `terraform apply`, state mutation,
Kubernetes resource creation, or application deployment was performed.

## Environment and dependency evidence

| Check | Command/scope | Result |
|---|---|---|
| AWS identity | `AWS_PROFILE=flash-sale-terraform aws sts get-caller-identity` | PASS; expected bootstrap account selected |
| Terraform initialization | `terraform init -upgrade=false -input=false -no-color` | PASS; existing S3 backend initialized |
| Provider selections | `terraform providers lock -platform=windows_amd64 -platform=linux_amd64` | PASS; Windows and Linux checksums recorded |
| Locked AWS provider | `.terraform.lock.hcl` | `hashicorp/aws` `5.100.0` |
| Reviewed modules | `vpc.tf`, `eks.tf` | VPC `5.21.0`; EKS `20.37.2` |

The concrete operator public IP was resolved only into the current process environment as a `/32`
and was not written to a repository file or saved plan.

## Input-boundary evidence

| Scenario | Command behavior | Result |
|---|---|---|
| Required CIDR omitted | `terraform plan -refresh=false -input=false` | PASS; rejected with exit status 1 |
| CIDR list empty | Plan with an explicit empty list | PASS; rejected with exit status 1 |
| Global IPv4 range | Plan with `0.0.0.0/0` | PASS; rejected with exit status 1 |
| Operator `/32` | Plan with a temporary local input | PASS; endpoint contains the `/32` and no endpoint global allow-list |

## Static and plan evidence

| Check | Result |
|---|---|
| `terraform fmt -recursive` | PASS |
| `terraform fmt -check -recursive` | PASS, exit status 0 |
| `terraform validate -no-color` | PASS, exit status 0 |
| Refreshed plan | PASS, exit status 0 |
| Plan summary | `33 to add, 21 to change, 0 to destroy` |
| Replacement actions | None |
| EKS version | `1.36` |
| Endpoint mode | Private access enabled; public access restricted to the temporary operator `/32` |
| Managed workers | Three desired `m7i-flex.large`, on-demand; min 2, max 4 |
| ECR hardening | Eight in-place `MUTABLE` to `IMMUTABLE` transitions; scan-on-push retained |
| Existing network changes | Thirteen in-place common-tag additions only; VPC/subnet/NAT/routing topology unchanged |
| EKS Auto Mode policy | Disabled; conventional managed node group retained |
| `git diff --check` | PASS, exit status 0; only Windows line-ending notices |
| Terraform ignore rules | Cache, state, variable, crash, and saved-plan probes ignored |
| `.terraform.lock.hcl` ignore check | PASS; lock file remains eligible for commit |

The 33 additions are the expected unapplied EKS foundation: cluster and logging resources,
security groups, IAM and access-entry resources, KMS encryption resources, and the managed node
group. No saved `.tfplan` artifact was created.

## Omitted validations

- Maven, HTTP/Kafka contract, and load tests are not applicable because no Java or runtime contract
  changed.
- Kubernetes client dry-run is not applicable because this feature adds no Kubernetes manifests.
- Runtime smoke tests begin only after a separately approved `terraform apply` and cluster access
  bootstrap.
