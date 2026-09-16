# AWS Terraform Foundation

This root module provisions the cost-bearing AWS foundation for the development cloud environment:

- a two-AZ VPC (`10.0.0.0/16`) with public and private subnets;
- one NAT gateway for the internship-scale cost profile;
- an EKS cluster named `flash-sale-dev` with private worker nodes;
- an on-demand managed node group (desired 3, min 2, max 4) using `m7i-flex.large`;
- EBS CSI IRSA/add-on for persistent volumes;
- one ECR repository per delivered application service;
- an optional ACM certificate and DNS validation outputs for the HTTPS Gateway edge.

Cloud resources incur charges. This module is currently not applied because the environment was
destroyed for cost control.

## Required operator CIDR

`cluster_endpoint_public_access_cidrs` has no unsafe default. Supply the current operator **public
internet IP** as `/32`; it is not a VPC subnet and must never be `0.0.0.0/0`.

```powershell
$PublicIp = (Invoke-RestMethod -Uri "https://api.ipify.org").Trim()
$env:AWS_PROFILE = "flash-sale-terraform"
$env:TF_VAR_cluster_endpoint_public_access_cidrs = ('["{0}/32"]' -f $PublicIp)
```

The repository helper can derive it locally with `-AutoDetectPublicIp`.

## Safe workflow

```powershell
cd infra/terraform
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan -input=false
```

Prefer the read-only safety gate from the repository root:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase23-terraform-gate.ps1 -AutoDetectPublicIp
```

Review every `replace` and `destroy` action before `terraform apply`. A plan can update resources in
place, replace them (destroy/create), or delete resources removed from configuration; it is a
preview, not a guarantee that nothing will be destroyed.

## HTTPS certificate

When `gateway_acm_enabled=true`, Terraform requests a certificate for `gateway_domain_name` and
outputs the DNS validation record. Publish that CNAME at the authoritative DNS provider, wait for
ACM `ISSUED`, and only then reconcile the Kubernetes TLS LoadBalancer. Domain registration/DNS
hosting is external to this module.

## State and secrets

- Do not commit credentials, generated plans, state backups, or secret tfvars.
- Treat Terraform state as sensitive even when no variable is named “secret”.
- Do not import or edit state casually; import only an existing resource that configuration already
  owns.
- Destroying EKS does not mean every external DNS record or third-party Stripe configuration is
  automatically removed.
