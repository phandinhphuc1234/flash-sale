# ADR 0008: Argo CD Owns Reconciliation of the Product Pilot

**Status**: Accepted
**Date**: 2026-08-21

## Context

Phase 9 created a six-resource Product/PostgreSQL overlay and proved it manually on EKS. Continuing
manual `kubectl apply` would leave Git and the cluster out of sync. The project needs a small GitOps
controller demonstration without exposing an administrative UI or taking ownership of secrets and
AWS resources.

## Decision

Install one non-HA Argo CD control plane in `argocd` using a pinned official manifest. Create one
Application named `dev-pilot` that reads `develop/infra/k8s/overlays/dev-pilot`, targets the in-cluster
`flash-sale` namespace, self-heals drift, and does not prune. Argo CD remains internal and is accessed
through port-forward.

Ownership boundaries:

- Terraform owns AWS/EKS/EBS CSI resources.
- Argo CD owns the six pilot Kubernetes resources.
- The operator owns external Secret values.

## Alternatives considered

- Public LoadBalancer for Argo CD: rejected; port-forward is sufficient.
- Argo CD pruning: deferred until a later reviewed safety policy.
- Full eight-service overlay: rejected for this pilot.
- Floating latest/stable manifest: rejected; version drift must be reviewed.

## Consequences

Positive:

- Git becomes the reconciliation source for the first live application slice.
- Drift and Application health are visible through Kubernetes/Argo CD status.
- No AWS ingress or secret-management dependency is added.

Trade-offs:

- The control plane is non-HA and not production hardened.
- The initial admin credential remains an operator-managed bootstrap secret.
- Image promotion still requires a reviewed Git change; CI automation is a later phase.

## Rollback

- Delete the `dev-pilot` Application only; leave the pilot workloads/PVC intact unless an explicit
  rollback plan is approved.
- Remove the Argo CD namespace only after confirming no other Applications exist.
