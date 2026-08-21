# Data Model: Product Pilot Image Promotion

| Entity | Identity | Required attributes | Owner | Lifecycle |
|---|---|---|---|---|
| Image Release | `repository + immutable tag` | source SHA, image URI, digest, workflow run | ECR/GitHub Actions | built → pushed → referenced or retained |
| Promotion Pull Request | GitHub PR number | source SHA, image tag, target branch, changed path | GitHub | opened → reviewed → merged or closed |
| Delivery Workflow Run | GitHub run ID | event, source ref, verification result, publish result, PR result | GitHub Actions | queued → verifying → publishing → promoting |
| Argo Application Revision | Git commit SHA | `develop` revision, image tag, sync status, health status | Argo CD | observed after desired-state merge |

Invariants:

- An immutable ECR tag is never overwritten.
- A failed verification cannot create an Image Release or Promotion Pull Request.
- A Promotion Pull Request changes only the Product pilot image tag.
- Argo CD is the only component that applies the merged desired state to Kubernetes.
- Credential values are not attributes in Git-tracked entities.
