# Terraform Bootstrap Contract

## Operator preconditions

- The current shell selects AWS profile `flash-sale-terraform`.
- STS reports the expected `flash-sale-terraform` identity.
- The operator supplies `cluster_endpoint_public_access_cidrs` locally.
- No concrete credential, profile path, access key, secret key, session token, or administrator CIDR
  is committed.

## Accepted inputs

| Input | Type | Required | Default |
|---|---|---:|---|
| `aws_region` | string | No | `ap-southeast-2` |
| `project_name` | string | No | `flash-sale` |
| `environment` | string | No | `dev` |
| `cluster_endpoint_public_access_cidrs` | list(string) | Yes | None |

The CIDR input must be non-empty, contain valid CIDR strings, and exclude `0.0.0.0/0`.

## Stable outputs

| Output | Shape |
|---|---|
| `aws_region` | string |
| `cluster_name` | string |
| `cluster_endpoint` | string |
| `private_subnet_ids` | list(string) |
| `ecr_repository_urls` | map(string) keyed by service name |

## Plan safety contract

A plan is acceptable only when:

- Terraform format and validation pass.
- No destroy or replacement action is present.
- EKS version is `1.36`.
- Public endpoint CIDRs equal the supplied local value and exclude `0.0.0.0/0`.
- Managed node desired capacity remains three `m7i-flex.large` on-demand instances.
- Existing VPC topology is unchanged.
- All eight ECR repositories are retained and transition only to immutable tags plus common tags.

`terraform apply` is not part of this contract.
