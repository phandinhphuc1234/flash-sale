# Supported Deployment Environments

The project has exactly two supported environments. `product` and `production` are not deployment
targets for this internship project.

| Environment | Source of truth | Runtime | How it is operated | Public endpoint |
|---|---|---|---|---|
| `local` | `infra/docker/` | Docker Compose on a developer machine | `docker compose` and the scripts under `infra/docker/` | No; use localhost/port mappings |
| `cloud` | `infra/k8s/overlays/cloud/` | AWS EKS | Argo CD reconciles the Git repository in a later phase | No in Phase 13; test Gateway with port-forward first |

## What each environment owns

### Local

- PostgreSQL, Redis, Kafka, and Schema Registry are started by `infra/docker/compose.yml` or the
  development compose variant.
- Service-specific values belong in the ignored `infra/docker/.env` file.
- Topic, schema, and smoke scripts under `infra/docker/` are the repeatable local bootstrap path.

### Cloud

- The canonical application composition is `infra/k8s/overlays/cloud/`.
- Shared resources remain in `infra/k8s/base/`.
- ECR image tags are promoted into the cloud overlay by CI/CD in a later phase.
- Secrets are created by an operator-only flow in a later phase. Secret values must never be committed
  to this repository.
- Database migrations are applied separately through `infra/k8s/overlays/cloud-migrations/` after the
  Phase 14 platform is ready and Phase 15 Secrets exist. The application overlay is not applied until
  those seven service-owned migration Jobs complete.
- `dev-pilot` is retained only as historical Product-pilot and rollback evidence. It is not a third
  supported environment.

## Phase 15 secret and configuration inventory

The following names are documented now so later provisioning can be complete. This file intentionally
contains names only, not values:

| Owner | Secret keys / material | First needed |
|---|---|---|
| Platform | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD` | Phase 15 |
| Gateway | `RATE_LIMIT_KEY_HMAC_SECRET` | Phase 15 |
| Authentication | `AUTH_THROTTLE_HMAC_SECRET`, JWT PEM files under `AUTH_JWT_KEY_DIR` | Phase 15 |
| OAuth clients | `CAMPAIGN_CLIENT_SECRET`, `FLASHSALE_CLIENT_SECRET` | Phase 15 |
| Payment | Stripe keys | Deferred; only with explicit `-EnableStripe` |
| Messaging | None currently; Kafka and Schema Registry are internal plaintext | ADR 0019 |

Values remain in local ignored files or an operator-controlled secret manager. The Phase 15
validator only prints names and file paths, never values. See `infra/CONFIGURATION.md` for the
per-service Secret boundaries and ConfigMap matrix.

## Promotion boundary

Local tests prove service behavior and backing-service bootstrap. Cloud promotion proves image
availability, Kubernetes readiness, Gateway routing, GitOps reconciliation, and rollback. A local
`.env` value is never copied into Git or into a public issue/PR.
