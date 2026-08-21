# Data Model: Argo CD Dev Pilot Bootstrap

| Object | Identity | Owner | Lifecycle |
|---|---|---|---|
| Argo CD installation | `argocd` namespace + pinned manifest version | Bootstrap script/operator | Install or in-place upgrade |
| Argo CD Application | `argocd/dev-pilot` | Git repository | Reconciles continuously |
| Git source | URL + `develop` + `infra/k8s/overlays/dev-pilot` | Application spec | Immutable source coordinates until reviewed change |
| Pilot destination | `flash-sale` namespace in in-cluster API | Application spec | Receives six Phase 9 resources |

Argo CD owns reconciliation of the six pilot resources after Application creation. Terraform owns
AWS/EKS storage resources. The operator owns external Secrets. No resource has two independent
desired-state owners.
