# Contract: Eight-Service Image Promotion

## Target contract

| Field | Rule |
|---|---|
| `name` | One of the eight cloud deployment keys |
| `module` | Maven module path; Flash Sale maps to `services/flashsale-service` |
| `repository` | Existing `flash-sale/<name>` ECR repository, with `flash-sale-service` special mapping |
| `tag` | `release-<full source commit SHA>` |
| desired state | Matching `newTag` in `infra/k8s/overlays/cloud/kustomization.yaml` only |

## Promotion PR contract

- Base branch: `develop`
- Automation branch: `automation/cloud-image-promotion-<sha12>-<run_id>`
- Allowed changed file: `infra/k8s/overlays/cloud/kustomization.yaml`
- Allowed changed entries: exactly one `newTag` per selected service
- PR body: source commit, selected services, image tags, ECR repository, and Argo reconciliation
  note
- Forbidden operations: direct `develop` push, `kubectl`, Argo API mutation, Secret value access,
  auto-merge

## Trigger contract

Service-local paths select that service. Changes to `pom.xml`, `mvnw`, `.mvn/`, `libs/`, `contracts/`,
any deployed service Dockerfile, or the delivery workflow select all eight.
