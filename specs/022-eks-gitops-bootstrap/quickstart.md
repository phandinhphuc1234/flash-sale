# Quickstart: Validate EKS Bootstrap Without Applying

## Prerequisites

- Terraform CLI compatible with `required_version`.
- AWS CLI profile `flash-sale-terraform`.
- An operator-approved public IPv4 `/32` for temporary `kubectl` access.

## 1. Select and verify the AWS identity

```powershell
$env:AWS_PROFILE = "flash-sale-terraform"
$env:AWS_REGION = "ap-southeast-2"
aws sts get-caller-identity
```

Expected ARN suffix: `user/flash-sale-terraform`.

## 2. Supply the endpoint CIDR locally

```powershell
$env:TF_VAR_cluster_endpoint_public_access_cidrs = '["203.0.113.10/32"]'
```

Replace the documentation address with the operator's current public IPv4 `/32`. Do not commit the
concrete value.

## 3. Initialize and validate

```powershell
Set-Location C:\Users\MSi\flash-sale\infra\terraform
terraform init
terraform fmt -check -recursive
terraform validate -no-color
```

Expected: all commands exit zero.

## 4. Generate a refreshed read-only plan

```powershell
terraform plan -lock=false -input=false -no-color
```

Expected:

- No destroy or replacement action.
- Existing VPC topology remains unchanged.
- Eight ECR repositories remain and become immutable.
- EKS `1.36` is created with private access and only the supplied public CIDR.
- Node group desired capacity is three `m7i-flex.large` on-demand workers.

Do not run `terraform apply` in this feature.

## 5. Git hygiene

```powershell
Set-Location C:\Users\MSi\flash-sale
git diff --check
git status --short
```

`.terraform/`, state, local tfvars, crash files, and saved plans must not appear. The dependency lock
file and Terraform source files should remain eligible for review/commit.
