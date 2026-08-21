# Contract: Argo CD Dev Pilot Application

| Field | Required value |
|---|---|
| Application name | `dev-pilot` |
| Namespace | `argocd` |
| Repository | `https://github.com/phandinhphuc1234/flash-sale.git` |
| Revision | `develop` |
| Path | `infra/k8s/overlays/dev-pilot` |
| Destination server | `https://kubernetes.default.svc` |
| Destination namespace | `flash-sale` |
| Sync | Automated + self-heal |
| Prune | Disabled |

The Application must not point at `infra/k8s/overlays/dev`, because that overlay contains all eight
services and is not approved for live deployment in this phase.
