# Implementation Plan: GitOps Stateful Product Pilot

**Branch**: `codex/gitops-phase9-stateful-pilot` | **Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)
**Status**: Approved for implementation

## Summary

Adopt the manually-created EBS CSI role/add-on in Terraform and create a dedicated Kustomize
`dev-pilot` overlay for one Product Service plus one PostgreSQL StatefulSet. Keep credentials
external and make validation the default for the Phase 9 PowerShell workflow.

## Technical Context

| Area | Decision |
|---|---|
| AWS root | Existing `infra/terraform` module and S3 backend |
| EKS | Existing `flash-sale-dev` in `ap-southeast-2` |
| Terraform | AWS provider/module versions already pinned by Phase 5/6 |
| Storage | AWS EBS CSI managed add-on, imported into Terraform |
| Database | `postgres:17-alpine`, one StatefulSet replica, 8 GiB `gp2` PVC |
| Kubernetes source | `infra/k8s/base/product-postgres` and `infra/k8s/overlays/dev-pilot` |
| Image | Existing immutable ECR tag `pilot-f5fa7cb` initially |
| Secrets | External Secret objects, name-only references |
| Operator workflow | `infra/scripts/gitops/phase9-stateful-pilot.ps1`; dry-run by default |

## Constitution Check

| Rule | Result | Evidence |
|---|---|---|
| Root infrastructure ownership | PASS | Terraform, Kustomize, and scripts stay under `infra/`. |
| Service/data ownership | PASS | Product owns this database; no cross-service query is introduced. |
| Controlled ingress | PASS | Product and PostgreSQL remain internal Services. |
| Secret safety | PASS | No Secret resource or value is committed. |
| Durable truth | PASS | PostgreSQL/PVC remains durable; Redis is unchanged. |
| Observability | PASS | Existing probes and `pg_isready` are declarative. |
| Kubernetes validation | REQUIRED | Render and client dry-run are tasks T009/T010. |
| Architecture decision | PASS | ADR 0007 records the stateful pilot boundary and rollback. |

## Design

### Terraform storage adoption

1. Enable the EKS module's `aws-ebs-csi-driver` cluster add-on and pass the existing service-account
   role ARN.
2. Add an IAM role trust policy restricted to the cluster OIDC provider and
   `system:serviceaccount:kube-system:ebs-csi-controller-sa`.
3. Attach only `AmazonEBSCSIDriverPolicyV2`.
4. Import the existing role, policy attachment, and EKS add-on before the first apply.
5. Require a plan review with zero destroy/replacement actions.

### Pilot overlay

The overlay has exactly six resources and does not include the full eight-service base. PostgreSQL
mounts the PVC at `/var/lib/postgresql/data` and sets `PGDATA` to the clean child directory. Product
uses the existing base probes/configuration and an immutable ECR image override.

### Script safety

`phase9-stateful-pilot.ps1` performs context checks, secret-name checks, render, and client dry-run
without `-Apply`. With `-Apply`, it applies only `infra/k8s/overlays/dev-pilot`, waits for the
StatefulSet/Deployment, and prints status. It never runs Terraform apply, writes secret values, or
deletes a PVC.

## Project Structure

```text
infra/terraform/
├── eks.tf                         # EBS CSI addon entry
└── ebs-csi.tf                     # role and policy attachment
infra/k8s/base/product-postgres/
├── kustomization.yaml
├── service.yaml
└── statefulset.yaml
infra/k8s/overlays/dev-pilot/
├── flash-sale-config.yaml
└── kustomization.yaml
infra/scripts/gitops/
└── phase9-stateful-pilot.ps1
docs/adr/0007-eks-stateful-product-pilot.md
```

## Validation Strategy

| Layer | Command | Expected |
|---|---|---|
| Terraform formatting | `terraform fmt -check -recursive` | exit 0 |
| Terraform validation | `terraform validate -no-color` | exit 0 |
| Terraform safety | `terraform plan -input=false` | zero destroy/replacement |
| Kustomize render | `kubectl kustomize infra/k8s/overlays/dev-pilot` | six resources |
| Client dry-run | `kubectl apply --dry-run=client -k infra/k8s/overlays/dev-pilot` | exit 0, no mutation |
| Live pilot | script with `-Apply` | PVC Bound; both workloads Ready |
| Safety | `git diff --check`, secret-kind scan | pass; no Secret values |

Maven tests are not applicable because no Java source or dependency changes occur.

## Complexity Tracking

The single-replica PostgreSQL decision is intentionally an accepted development exception to
production HA. ADR 0007 documents the simpler alternative and replacement path.
