# Contract: Terraform Storage and Product Pilot Desired State

## Terraform ownership contract

- Terraform root: `infra/terraform`.
- Managed add-on key: `aws-ebs-csi-driver`.
- Service-account role: `AmazonEKS_EBS_CSI_DriverRole`.
- Attached policy: `arn:aws:iam::aws:policy/AmazonEBSCSIDriverPolicyV2`.
- Existing AWS identities must be imported before apply.
- A plan proposing replacement or destruction is invalid for this feature.

## Kubernetes pilot contract

| Resource | Expected identity | Exposure |
|---|---|---|
| Namespace | `flash-sale` | Cluster scope |
| ConfigMap | `flash-sale-runtime-config` | Non-secret settings |
| StatefulSet | `product-postgres` | One replica |
| Headless Service | `product-postgres` | Internal DNS only |
| Deployment | `product-service` | One replica, immutable ECR image |
| Service | `product-service` | ClusterIP on port 8080 |

Required references:

- PostgreSQL credentials: Secret name `product-postgres-credentials`.
- Product runtime credentials: Secret name `flash-sale-secrets`.
- PostgreSQL data directory: `/var/lib/postgresql/data/pgdata`.
- Product health paths: `/actuator/health/liveness` and `/actuator/health/readiness`.
