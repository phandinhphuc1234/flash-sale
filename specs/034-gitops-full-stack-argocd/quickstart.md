# Phase 19 Quickstart

Prerequisites:

- Phase 17 is merged into develop.
- Phase 18 Gateway smoke passed.
- kubectl points at flash-sale-dev.
- dev-pilot is Synced/Healthy and has no resource-deletion finalizer.

From the repository root:

    .\infra\scripts\gitops\phase19-argocd-cloud.ps1

After the Phase 18 and Phase 19 pull requests are reviewed and merged into `develop`:

    git fetch origin develop
    git switch develop
    git pull --ff-only origin develop
    .\infra\scripts\gitops\phase19-argocd-cloud.ps1 -Apply -TimeoutSeconds 600

Validation mode changes nothing. Apply mode performs the guarded ownership handoff.

Inspect the result:

    kubectl -n argocd get applications
    kubectl -n argocd get application flash-sale-cloud
    kubectl -n flash-sale get deployments
    .\infra\scripts\gitops\phase18-gateway-smoke.ps1 -Run

Expected result:

- flash-sale-cloud is Synced and Healthy.
- dev-pilot no longer exists.
- Eight application Deployments are available.
- Product uses the approved immutable pilot tag.
- Gateway smoke remains readiness 200, catalog 200, admin 401.
