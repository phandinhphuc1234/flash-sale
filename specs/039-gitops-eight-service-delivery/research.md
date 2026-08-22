# Research: Eight-Service GitOps Image Delivery

## Existing State

- `ci.yml` already performs selective Maven verification, but it includes source-only `cart-service`
  and `notification-service` in its verification inventory.
- `product-pilot-delivery.yml` is the only hosted image publication workflow and updates the
  historical `dev-pilot` overlay.
- Phase 19 made `flash-sale-cloud` and `infra/k8s/overlays/cloud` the active cloud source of truth.
- The cloud Kustomize overlay contains eight image entries and the ECR repositories already exist.
- `github-ci-role` and GitHub OIDC are already used successfully by the Product workflow.

## Decisions

### One workflow owns cloud promotion

Keep one matrix workflow for all eight services. Retaining a Product trigger beside a generalized
workflow would cause duplicate Product pushes and competing PRs. The Product-only hosted workflow
is therefore retired while its historical spec and validation remain unchanged.

### Detect before building

Service-local paths select one target. Root Maven files, `libs/`, `contracts/`, Docker build inputs,
and the delivery workflow select all eight. This is conservative for shared build behavior and avoids
publishing an image that was not tested against a changed shared input.

### Promotion is one PR

The matrix publishes images in parallel, then one job changes only selected cloud image tags. This
keeps a shared change atomic at the Git desired-state boundary and makes review straightforward.

### Manual dispatch is explicit

Manual runs accept `all` or one canonical service key. No operator secret or local `.env` value is
needed; AWS access remains short-lived OIDC.

### No public or production semantics

This feature only delivers artifacts to the existing EKS dev cloud environment. Public Gateway,
Stripe cloud, observability, and final E2E remain canonical roadmap phases 23–26.
