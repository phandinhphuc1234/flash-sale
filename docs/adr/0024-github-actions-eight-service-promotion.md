# ADR 0024: Generalize GitHub Actions Image Promotion to Eight Cloud Services

**Status**: Accepted
**Date**: 2026-08-22

## Context

Phase 11 proved a Product-only workflow that built an image, pushed ECR, and opened a PR updating
the historical `dev-pilot` overlay. Phase 19 made `flash-sale-cloud` and
`infra/k8s/overlays/cloud` the active cloud source of truth, while seven other deployed services
still required manual image publication and tag changes. Leaving the Product trigger active beside a
new generalized workflow would create duplicate Product pushes and competing PRs.

## Decision

Use one matrix-based GitHub Actions workflow for the eight deployed services. It detects affected
targets, verifies each selected Maven reactor, assumes the existing AWS role through GitHub OIDC,
publishes immutable commit-derived ECR images, and opens one reviewed PR that changes only selected
image tags in the cloud overlay. Argo CD remains the sole Kubernetes reconciler after the PR merges.

The historical Product-only hosted trigger is retired. Its spec, local helper, pilot overlay, and
validation remain as historical rollback evidence and are not deleted by this decision.

## Alternatives considered

- **Keep Product-only workflow and add seven more workflows**: rejected because duplicated logic would
  create competing PRs and drift between services.
- **Push directly to `develop` or call `kubectl`**: rejected because it bypasses branch protection
  and Argo GitOps ownership.
- **Use mutable `latest` tags**: rejected because the deployed artifact would not be traceable to a
  source revision.
- **Build all eight for every service-local change**: rejected because selective delivery is the
  approved monorepo optimization; shared changes still fan out to all eight.

## Consequences

Positive:

- All eight cloud services share one auditable source-to-ECR-to-PR-to-Argo path.
- Service-local changes remain selective; shared inputs are conservatively fanned out.
- One PR gives reviewers an atomic desired-state view of a shared release.
- No new AWS, Kubernetes, application, or production dependency is introduced.

Trade-offs:

- A shared change may build eight images and consume more runner time.
- The existing GitHub OIDC role must allow push to all eight ECR repositories.
- Promotion still requires a human review and merge.

## Rollback

Close the promotion PR before merge, or revert the merged cloud-overlay commit. Argo CD will
reconcile the previous image tags. ECR images are retained; deletion remains outside this ADR.
