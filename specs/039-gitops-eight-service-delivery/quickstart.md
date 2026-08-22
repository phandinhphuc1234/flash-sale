# Quickstart: Eight-Service GitOps Image Delivery

## Prerequisites

- The Phase 23 Terraform gate and `flash-sale-cloud` Argo Application are healthy.
- The eight ECR repositories exist and `github-ci-role` trusts this repository's GitHub OIDC subject.
- GitHub Actions repository settings allow workflow-created branches and pull requests.
- Do not add AWS, Stripe, JWT, database, or Kubernetes Secret values to the repository.

## Validate locally

```powershell
git diff --check
kubectl kustomize infra/k8s/overlays/cloud | Out-Null
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
.\mvnw.cmd clean verify
```

## Run hosted delivery

1. Push a service change to `develop`, or open **Actions → Eight-Service GitOps Delivery → Run
   workflow**.
2. For a manual run, select `all` or one canonical service key.
3. Review the detector output and every `verify_and_publish` matrix job.
4. Review the generated automation PR. It must change only the selected `newTag` entries in the
   cloud overlay.
5. Merge the PR only after the required `Maven Verify` check is green.
6. Observe Argo:

```powershell
kubectl -n argocd get application flash-sale-cloud --watch
kubectl -n flash-sale get deployments
```

7. Run the release evidence check:

```powershell
.\infra\scripts\gitops\phase21-cloud-release-verify.ps1
```

The workflow does not read or print `.env`, Kubernetes Secret, JWT, Stripe, AWS key, or repository
Secret values. It never runs `kubectl`.
