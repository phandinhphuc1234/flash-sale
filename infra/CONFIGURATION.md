# Runtime configuration inventory

Phase 15 is the cloud configuration boundary for the eight deployed Spring Boot services. The
ignored `infra/docker/.env` remains the operator's local input; no value from it belongs in Git.

## Inputs that must be entered manually now

| Local input | Kubernetes owner | How it is used |
|---|---|---|
| `POSTGRES_USER` | `platform-secrets` and each service Secret | PostgreSQL bootstrap and datasource username |
| `POSTGRES_PASSWORD` | `platform-secrets` and each service Secret | PostgreSQL bootstrap and datasource password |
| `REDIS_PASSWORD` | `platform-secrets`, `gateway-secrets`, `flashsale-secrets` | Redis authentication and rate-limit/flash-sale clients |
| `RATE_LIMIT_KEY_HMAC_SECRET` | `gateway-secrets` | Gateway rate-limit key hashing |
| `AUTH_THROTTLE_HMAC_SECRET` | `authentication-secrets` | Authentication failure-throttle hashing |
| `CAMPAIGN_CLIENT_SECRET` | `authentication-secrets`, `campaign-secrets` | OAuth client credentials |
| `FLASHSALE_CLIENT_SECRET` | `authentication-secrets`, `flashsale-secrets` | OAuth client credentials |
| `AUTH_JWT_KEY_DIR/jwt-public.pem` | `auth-jwt` | Authentication public signing key |
| `AUTH_JWT_KEY_DIR/jwt-private.pem` | `auth-jwt` | Authentication private signing key |

`POSTGRES_USER` and `POSTGRES_PASSWORD` are copied into service-scoped Secrets by the provisioning
script because each service has its own database URL. This does not give a service access to another
service's database.

## Deferred inputs

Stripe is deliberately disabled in Phase 15. `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, and
`STRIPE_WEBHOOK_SECRET` are required only when the later Payment enablement phase is explicitly run
with `phase15-secrets.ps1 -EnableStripe` (or its replacement enablement script). Do not add them to
the cloud Secret yet. Public HTTPS origins, secure cookies, migrations, and external secret-manager
integration are also later phases.

## Secret boundaries

| Kubernetes Secret | Consumers | Keys/material |
|---|---|---|
| `platform-secrets` | PostgreSQL, Redis | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD` |
| `gateway-secrets` | API Gateway | `SPRING_DATA_REDIS_PASSWORD`, `RATE_LIMIT_KEY_HMAC_SECRET` |
| `authentication-secrets` | Authentication | datasource/Redis passwords, throttle HMAC, Campaign/Flash Sale OAuth secrets |
| `product-secrets` | Product | datasource username/password |
| `campaign-secrets` | Campaign | datasource username/password, Campaign OAuth secret |
| `flashsale-secrets` | Flash Sale | datasource username/password, Redis password, Flash Sale OAuth secret |
| `inventory-secrets` | Inventory | datasource username/password |
| `order-secrets` | Order | datasource username/password |
| `payment-secrets` | Payment | datasource username/password; Stripe keys only when enabled later |
| `auth-jwt` | Authentication | `jwt-public.pem`, `jwt-private.pem` mounted as files |

The legacy `flash-sale-secrets` Secret may still exist for the pilot overlay, but no Deployment in
the canonical cloud overlay consumes it.

## Non-secret ConfigMaps

Every cloud application Pod receives `flash-sale-runtime-config`, its service ConfigMap under
`infra/k8s/overlays/cloud/config/`, and its own Secret. ConfigMaps contain only internal DNS names,
datasource URLs, Kafka/Schema Registry endpoints, JWT metadata, topics, and safe feature flags.
Liquibase and Payment/Stripe processing remain disabled in this phase. Kafka and Schema Registry are
internal plaintext services under ADR 0019; no credentials are currently required.

## Local-only and optional values

Host bind addresses, published ports, Docker image names, `AUTH_JWT_KEY_DIR`, Stripe CLI values, and
developer debug settings are local/operator inputs. Optional timeout, retry, batch-size, retention,
and topic overrides have application defaults and are not missing secrets. They should be added to a
Cloud ConfigMap only when a later approved task changes the default intentionally.

Cart and Notification are not part of the approved cloud eight-service topology, so Phase 15 does
not create their ConfigMaps or Secrets.

## Safe workflow

```powershell
./infra/scripts/gitops/phase15-secrets.ps1
kubectl kustomize infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

The first command validates key names and JWT file paths without printing values or changing the
cluster. Only after reviewing the rendered manifests should an operator intentionally run it with
`-Apply`. Never commit `infra/docker/.env`, PEM files, generated Secret YAML, or command output that
contains secret data.
