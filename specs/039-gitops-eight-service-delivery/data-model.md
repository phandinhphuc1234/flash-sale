# Data Model: Eight-Service GitOps Image Delivery

| Entity | Identity | Required attributes | Lifecycle |
|---|---|---|---|
| Delivery Target | Canonical deployment key | module, Dockerfile, ECR repository, overlay image key | Static workflow mapping |
| Image Release | `(service, source SHA)` | immutable tag, registry, digest | Built → pushed → referenced by PR → reconciled by Argo |
| Promotion Pull Request | Git PR number/branch | source SHA, selected targets, image tags, overlay path | Open → reviewed/merged or closed |
| Delivery Run | GitHub run ID | event, base/head SHA, selected targets, job outcomes | Detected → verified → published → promoted/failed |

No application database, Kubernetes Secret, Kafka topic, or domain entity is introduced by this
feature.
