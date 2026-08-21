# ADR 0020: Cloud Secret and ConfigMap Boundaries

**Status**: Accepted

**Date**: 2026-08-21

## Context

The first cloud overlay used one `flash-sale-secrets` reference for all application Pods. The full
repository requires unrelated credentials: database/Redis credentials, Gateway HMAC, Authentication
JWT/OAuth material, and Payment Stripe keys. A global Secret would make every Pod eligible to read
credentials it does not need and would not support different datasource URLs through one ConfigMap.

## Decision

The canonical `cloud` overlay uses one non-secret runtime ConfigMap per application service and one
Secret per platform/service boundary:

- `platform-secrets`
- `gateway-secrets`
- `authentication-secrets`
- `product-secrets`
- `campaign-secrets`
- `flashsale-secrets`
- `inventory-secrets`
- `order-secrets`
- `payment-secrets`
- `auth-jwt`

Each application Pod receives the common runtime ConfigMap, its service ConfigMap, and only its own
Secret. Authentication receives `auth-jwt` as a mounted file Secret. PostgreSQL and Redis reference
`platform-secrets`. The old global `flash-sale-secrets` remains only for legacy overlays and is not a
cloud source of truth.

The operator script reads the ignored local `.env` and JWT files only when run locally. It validates
by default and mutates the cluster only with an explicit `-Apply`. It never prints Secret values.

Stripe keys are kept in `payment-secrets` only when the operator explicitly opts into
`-EnableStripe`; the Phase 15 Payment ConfigMap keeps provider flags disabled.

## Alternatives Considered

### One shared Secret and one shared ConfigMap

Rejected because datasource URLs collide and all Pods receive unnecessary credentials, including
Stripe and JWT material.

### External Secrets Operator immediately

Deferred because it adds a new production dependency and AWS secret-store lifecycle. The internship
baseline uses an operator-only script; a later ADR can adopt AWS Secrets Manager.

### Store PEM contents in ConfigMap

Rejected because private key material is sensitive and must be a file Secret with least-privilege
mounting.

## Consequences

### Positive

- Secret ownership is visible in Kubernetes manifests and reviewable in Git.
- Payment credentials do not reach unrelated services.
- Service-specific datasource and DNS configuration is explicit.
- Validation can detect missing operator inputs before a rollout.

### Negative and limitations

- More Secret/ConfigMap resources must be kept in sync.
- The operator must run the provisioning script and rotate values manually.
- Stripe and migration rollout are intentionally separate phases.

## Migration and Rollback

The cloud overlay replaces its global Secret references through Kustomize patches. Legacy `dev` and
`dev-pilot` overlays remain unchanged. Removing the Phase 15 config/patch resources reverts the cloud
overlay to its previous render, but Secret deletion remains an explicit operator action.
