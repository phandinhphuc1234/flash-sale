# Kubernetes Desired State

This directory owns Kustomize bases, cloud overlays, one-off migration Jobs, and Argo CD Application
objects for the AWS EKS environment. It does not own application source or service database
migrations.

> The EKS cluster is currently absent after cost-control cleanup. These files are desired state, not evidence
> that a live cluster exists today.

## Layout

```text
infra/k8s/
├── base/                    # reusable Namespace, Deployment, and Service resources
├── overlays/
│   ├── cloud/               # canonical local-or-cloud model's AWS desired state
│   ├── cloud-migrations/    # explicit one-off Liquibase Jobs
│   ├── dev/                 # local validation overlay
│   └── dev-pilot/           # historical Product pilot evidence
└── argocd/                  # application ownership and observability application
```

The canonical cloud overlay deploys the active application services plus PostgreSQL, Redis, Kafka,
and Schema Registry. Stateful data uses PVCs; platform/application Services remain ClusterIP except
the explicitly reviewed TLS Gateway edge.

## Apply order

```text
Terraform/EKS -> kubeconfig and cluster health
              -> platform + Secret/config provisioning
              -> explicit migration Jobs
              -> Kafka topics and Schema Registry subjects
              -> Argo CD application desired state
              -> application rollout and smoke tests
```

Never let every application replica run Liquibase on startup. Review expand/contract compatibility,
backup needs, and old-image compatibility before a cloud schema change.

## Validation

These commands render or dry-run only; they do not prove live health:

```powershell
kubectl kustomize infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud-migrations
```

Live mutation must follow the reviewed helpers in
[`infra/scripts/gitops/README.md`](../scripts/gitops/README.md). Secrets are created from ignored
local inputs and must never appear in Kustomize files, logs, or Git.

## GitOps ownership

`flash-sale-cloud` tracks `develop` and the cloud overlay. GitHub Actions publishes immutable ECR
images and opens a promotion PR; merging that PR changes Git desired state, then Argo CD reconciles
EKS. Do not run ad-hoc `kubectl set image` as a normal delivery path because Argo will restore Git.
