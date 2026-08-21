# Data Model: GitOps Stateful Product Pilot

This feature models infrastructure objects rather than application domain entities.

| Object | Owner | Identity | Lifecycle | Durable data |
|---|---|---|---|---|
| EBS CSI IAM role | Terraform/AWS | `AmazonEKS_EBS_CSI_DriverRole` | Imported, managed, retained | IAM trust/policy metadata |
| EBS CSI add-on | Terraform/EKS | cluster + `aws-ebs-csi-driver` | Imported, updated in place | Controller configuration |
| Product PostgreSQL StatefulSet | Kustomize/Kubernetes | namespace + `product-postgres` | One replica for pilot | Runs PostgreSQL process |
| Product PostgreSQL PVC | Kubernetes/EBS CSI | `pgdata-product-postgres-0` | Bound and retained | Product database files |
| Product Service Deployment | Kustomize/Kubernetes | namespace + `product-service` | Immutable image rollout | Stateless process |
| External Secret reference | Operator/Kubernetes | Secret name + namespace | Created outside Git | Credential values never stored here |

The PVC is the durable boundary. The StatefulSet may be recreated, but the claim must not be deleted
by the Phase 9 workflow.
