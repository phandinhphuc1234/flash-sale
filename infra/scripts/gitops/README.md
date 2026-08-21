# GitOps Phase 5–11 helper scripts

These PowerShell scripts preserve the manual workflow used for the AWS EKS GitOps exercise.
Run them from any directory. They resolve the repository root from their own location.

## Safety rules

- Phase 5 runs Terraform plan by default. Add -Apply only after reviewing the plan.
- Phase 7 only renders and runs Kubernetes client-side dry-run. It never creates resources.
- Phase 8 image publishing requires -Push.
- Phase 8 pilot deployment requires -Apply and prompts for a PostgreSQL password at runtime.
- Phase 11 Product delivery builds locally by default; ECR publishing requires -Push and desired-state
  editing requires -UpdateOverlay. The helper never commits or pushes Git changes.
- Passwords, tfvars, kubeconfig files, and .env files are never written by these scripts.
- Do not run the Phase 7 full dev overlay against EKS yet; it contains all eight services.

## Sequence

From the repository root:

    .\infra\scripts\gitops\phase5-terraform.ps1
    .\infra\scripts\gitops\phase5-terraform.ps1 -Apply

    .\infra\scripts\gitops\phase6-eks-health.ps1
    .\infra\scripts\gitops\phase7-kustomize-validate.ps1

    .\infra\scripts\gitops\phase8-product-image.ps1 -Push

    $image = "090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:pilot-<git-sha>"
    .\infra\scripts\gitops\phase8-product-pilot.ps1 -Image $image -Apply

    .\infra\scripts\gitops\phase11-product-delivery.ps1
    .\infra\scripts\gitops\phase11-product-delivery.ps1 -Push -UpdateOverlay
    kubectl kustomize infra/k8s/overlays/dev-pilot

Phase 8 pilot creates one PostgreSQL StatefulSet with an 8 GiB gp2 PVC, creates runtime Secrets
from an interactive password prompt, and applies only the product-service base. It is intentionally
a manual pilot and is not yet the GitOps desired-state source for Argo CD.

Phase 11 adds the hosted `Product Pilot Delivery` workflow. It verifies Product Service, uses the
existing GitHub OIDC role to push an immutable ECR image, and opens a pull request that changes only
the Product pilot image tag. Argo CD reconciles after that pull request is reviewed and merged.

## Required local tools

AWS CLI, Terraform, kubectl, Docker Desktop, Maven wrapper, and an AWS profile with the EKS/ECR
permissions must be available in PATH. The default profile is flash-sale-terraform and the default
region is ap-southeast-2.

The existing local deletion of infra/k8s/README.md is unrelated to these scripts and is never staged
by the scripts.
