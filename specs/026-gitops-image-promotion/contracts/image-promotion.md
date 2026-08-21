# Contract: Product Pilot Image Promotion

## Workflow trigger contract

| Item | Value |
|---|---|
| Automatic source | Push to protected `develop` after merge |
| Automatic paths | `services/product-service/**`, `pom.xml`, `mvnw`, `.mvn/**`, `libs/**`, delivery workflow |
| Manual source | `workflow_dispatch` |
| Verification | `./mvnw --batch-mode --no-transfer-progress -pl services/product-service -am verify` |
| AWS authentication | GitHub OIDC → existing `github-ci-role` |
| ECR repository | `flash-sale/product-service` |
| Image tag | `pilot-<full Git SHA>` |
| Desired-state PR base | `develop` |

## Pull request contract

The automation branch changes only:

```text
infra/k8s/overlays/dev-pilot/kustomization.yaml
```

The only semantic change is the Product Service `newTag`. The PR description includes the source
SHA, image URI, and the fact that Argo CD will reconcile after merge. The workflow never pushes this
change directly to `develop`, dispatches the existing `ci.yml` on the automation branch, and never
invokes `kubectl apply`.

## Kubernetes reconciliation contract

After the promotion PR is merged, the existing Argo Application reads:

- repository: `https://github.com/phandinhphuc1234/flash-sale.git`
- revision: `develop`
- path: `infra/k8s/overlays/dev-pilot`

The expected postcondition is `dev-pilot` = `Synced` + `Healthy`. The operator-managed repository
Secret and application runtime Secrets remain outside this contract.
