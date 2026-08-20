# Data Model: EKS GitOps Bootstrap Hardening

This feature has no business data model. The following configuration entities define the
infrastructure state reviewed by Terraform.

## OperatorInput

| Field | Meaning | Validation | Persistence |
|---|---|---|---|
| `aws_region` | AWS region containing the existing backend and infrastructure | Non-empty; defaults to `ap-southeast-2` | Version-controlled default |
| `project_name` | Ownership/cost-allocation project tag | Non-empty; defaults to `flash-sale` | Version-controlled default |
| `environment` | Environment ownership tag | Non-empty; defaults to `dev` | Version-controlled default |
| `cluster_endpoint_public_access_cidrs` | Networks allowed to access the public EKS API | Non-empty IPv4 CIDR list; excludes `0.0.0.0/0` | Local tfvars/environment only |

## EksClusterDesiredState

| Field | Value |
|---|---|
| Name | `flash-sale-dev` |
| Kubernetes version | `1.36` |
| VPC placement | Existing `flash-sale-vpc` private subnets |
| Endpoint mode | Private enabled; public enabled with explicit CIDRs |
| Creator administration | Existing Terraform bootstrap identity receives cluster admin access |
| Secret encryption | Module-managed KMS configuration |
| Control-plane logs | API, audit, and authenticator |

## ManagedNodeGroupDesiredState

| Field | Value |
|---|---|
| Name | `general` |
| Instance type | `m7i-flex.large` |
| Capacity | On-demand |
| Minimum | 2 |
| Desired | 3 |
| Maximum | 4 |
| Placement | Existing private subnets in two AZs |

## EcrRepositoryDesiredState

Eight repositories under `flash-sale/` retain scan-on-push and use immutable tags. Repository
lifecycle deletion is absent from this feature.

## TerraformOutput

| Output | Consumer |
|---|---|
| `aws_region` | Operator and later CI workflow |
| `cluster_name` | `aws eks update-kubeconfig` |
| `cluster_endpoint` | Operator diagnostics |
| `private_subnet_ids` | Later workload/platform design |
| `ecr_repository_urls` | Later release workflow and Kustomize image configuration |

## State Transitions

```text
Existing: S3 backend + VPC + eight mutable ECR repositories
  -> plan only: supported/restricted EKS + immutable ECR + tags/outputs
  -> future explicit apply: AWS state changes
```

The final transition is outside this feature.
