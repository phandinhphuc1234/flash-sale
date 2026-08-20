# Quickstart: Validate the Kubernetes Source Baseline

Run from the repository root.

## 1. Render without contacting or changing EKS

    kubectl kustomize infra/k8s/overlays/dev

Expected: one Namespace, one ConfigMap, eight Deployments, and eight Services.

## 2. Validate Kubernetes object shape without creating resources

    kubectl apply --dry-run=client -k infra/k8s/overlays/dev

Expected: each listed resource says created (dry run).

## 3. Guard against whitespace errors

    git diff --check

## Important boundary

Do not run kubectl apply -k infra/k8s/overlays/dev yet. The application dependencies, external
Secret content, immutable images, and api-gateway exposure are intentionally deferred to later
approved phases.
