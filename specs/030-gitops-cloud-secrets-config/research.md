# Research: Cloud Secrets and Service Configuration

## Decision 1 — Use per-boundary Kubernetes Secrets

- **Decision**: Create `platform-secrets`, `gateway-secrets`, `authentication-secrets`,
  `product-secrets`, `campaign-secrets`, `flashsale-secrets`, `inventory-secrets`,
  `order-secrets`, `payment-secrets`, and `auth-jwt`.
- **Reason**: The old shared `flash-sale-secrets` exposes all keys to every Pod and cannot express
  least privilege. Per-boundary names make the owner visible and prevent Stripe values from reaching
  unrelated services.
- **Rejected alternative**: Keep one shared Secret for simplicity. It would violate the PCI-aware
  boundary and make accidental exposure likely.

## Decision 2 — Commit non-secret Cloud ConfigMaps

- **Decision**: Commit one ConfigMap per cloud application service under
  `infra/k8s/overlays/cloud/config/`.
- **Reason**: Datasource URLs, internal DNS names, topic names, feature flags, and JWT locations are
  not credentials and need reviewable, deterministic values.
- **Rejected alternative**: Put every optional application property into one global ConfigMap. It
  creates collisions (`SPRING_DATASOURCE_URL`) and obscures which service consumes a value.

## Decision 3 — Use local ignored `.env` only as operator input

- **Decision**: A validation-only script reads key names/values from `infra/docker/.env` without
  printing values; an explicit `-Apply` creates Kubernetes Secrets through temporary filtered files.
- **Reason**: This lets the user keep secrets locally without asking the agent to receive them or
  committing them.

## Decision 4 — Defer migrations and Stripe enablement

- **Decision**: Cloud ConfigMaps set Liquibase and Payment/Stripe runtime flags to safe disabled
  values. Later phases run service-owned migration Jobs and explicitly enable Stripe.
- **Reason**: Starting eight services and migrations simultaneously would make failures ambiguous;
  Stripe keys are not needed to prove the platform and Gateway topology.
