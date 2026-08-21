# Quickstart: GitOps Stateful Product Pilot

Run from the repository root and keep the AWS profile explicit.

## 1. Confirm context and external secrets

```powershell
kubectl config current-context
kubectl -n flash-sale get secret product-postgres-credentials flash-sale-secrets
```

Do not print Secret values. If either Secret is absent, stop and provision it through the approved
local procedure.

Current Phase 8 adoption inventory:

- EKS cluster: `flash-sale-dev` (`ap-southeast-2`)
- IAM role: `AmazonEKS_EBS_CSI_DriverRole`
- EKS add-on: `aws-ebs-csi-driver`
- Existing claim: `pgdata-product-postgres-0` (8 GiB, `gp2`)
- Tested Product image: `flash-sale/product-service:pilot-f5fa7cb`

## 2. Validate without mutation

```powershell
.\infra\scripts\gitops\phase9-stateful-pilot.ps1 `
  -Image "090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:pilot-f5fa7cb"
```

## 3. Apply the pilot explicitly

```powershell
.\infra\scripts\gitops\phase9-stateful-pilot.ps1 `
  -Image "090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:pilot-f5fa7cb" `
  -Apply
```

## 4. Verify

```powershell
kubectl -n flash-sale get pods,svc,pvc
kubectl -n flash-sale rollout status statefulset/product-postgres
kubectl -n flash-sale rollout status deployment/product-service
```

Terraform import and plan must be completed before managing the EBS add-on through Terraform.

For PowerShell, do not use the literal placeholder `<PUBLIC_IP>`. Set the current public address as
an HCL list string (the value remains local and is never committed):

```powershell
$PublicIp = (Invoke-RestMethod -Uri "https://checkip.amazonaws.com").Trim()
$env:TF_VAR_cluster_endpoint_public_access_cidrs = ('["{0}/32"]' -f $PublicIp)
$env:TF_VAR_cluster_endpoint_public_access_cidrs
```

The last command should print a value shaped like `[`"203.0.113.10/32`"]`, not
`<PUBLIC_IP>/32`.

## Terraform adoption imports

After the Terraform resources in `infra/terraform/ebs-csi.tf` exist, run these from
`infra/terraform` (the role and add-on already exist in AWS):

```powershell
terraform import aws_iam_role.ebs_csi `
  AmazonEKS_EBS_CSI_DriverRole

terraform import aws_iam_role_policy_attachment.ebs_csi `
  "AmazonEKS_EBS_CSI_DriverRole/arn:aws:iam::aws:policy/AmazonEBSCSIDriverPolicyV2"

terraform import aws_eks_addon.ebs_csi `
  flash-sale-dev:aws-ebs-csi-driver
```

Use the account/cluster values from `terraform output` if they differ. Review the subsequent plan;
do not apply if it proposes a destroy or replacement.
