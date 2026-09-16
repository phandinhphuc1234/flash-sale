# Deployment and Delivery

This project uses Docker Compose locally and GitOps-managed AWS EKS for its single cloud development
environment. The EKS cluster is currently absent after cost-control cleanup; manifests and historical
evidence remain in Git.

## Read in this order

1. [`container-compose-k8s-strategy.md`](container-compose-k8s-strategy.md) — ownership and local to
   Kubernetes mapping.
2. [`../../infra/docker/README.md`](../../infra/docker/README.md) — local startup, migrations, and
   direct debug ports.
3. [`../../infra/terraform/README.md`](../../infra/terraform/README.md) — VPC/EKS/ECR and safe plan.
4. [`../../infra/k8s/README.md`](../../infra/k8s/README.md) — desired state and apply order.
5. [`../../infra/scripts/gitops/README.md`](../../infra/scripts/gitops/README.md) — guarded scripts.
6. [`gitops-roadmap-status.md`](gitops-roadmap-status.md) — historical phase ledger.

## Delivery flow

```text
source PR
  -> CI verifies affected Maven modules
  -> merge to develop
  -> delivery workflow builds/pushes immutable ECR tags
  -> workflow opens an image-promotion PR
  -> human reviews and merges desired-state change
  -> Argo CD reconciles cloud overlay
  -> rollout, digest, HTTP, Saga, and rollback verification
```

The two merges are intentional: the first approves source; the second approves exactly which built
artifact the cloud should run. This keeps deployment auditable and makes Git revert the normal image
rollback mechanism.

## Migration rule

Application Deployments do not own migration execution. Run the reviewed one-off Liquibase Jobs
before a new image that requires the schema. Use expand/contract compatibility when old and new pods
can overlap; an image rollback cannot automatically undo destructive SQL.

## Evidence rule

Kustomize dry-run proves renderability. Argo Synced/Healthy proves desired-state reconciliation.
Neither proves checkout, webhook, Kafka, stock, or Order correctness. Record the relevant business
smoke and recovery result in the feature `validation.md`.
