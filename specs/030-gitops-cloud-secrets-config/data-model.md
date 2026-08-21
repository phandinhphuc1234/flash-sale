# Data Model: Cloud Secret and ConfigMap Boundaries

## Kubernetes Secret inventory

| Secret | Owner | Keys supplied manually or derived |
|---|---|---|
| `platform-secrets` | PostgreSQL/Redis platform | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD` |
| `gateway-secrets` | API Gateway | `SPRING_DATA_REDIS_PASSWORD`, `RATE_LIMIT_KEY_HMAC_SECRET` |
| `authentication-secrets` | Authentication | DB username/password, Redis password, throttle HMAC, Campaign/Flash-sale client secrets |
| `product-secrets` | Product | DB username/password |
| `campaign-secrets` | Campaign | DB username/password, `CAMPAIGN_CLIENT_SECRET` |
| `flashsale-secrets` | Flash Sale | DB username/password, Redis password, `FLASHSALE_CLIENT_SECRET` |
| `inventory-secrets` | Inventory | DB username/password |
| `order-secrets` | Order | DB username/password |
| `payment-secrets` | Payment | DB username/password; Stripe keys only with `-EnableStripe` |
| `auth-jwt` | Authentication | `jwt-public.pem`, `jwt-private.pem` files |

## Cloud ConfigMap inventory

Each of the eight application Deployments receives:

1. `flash-sale-runtime-config` for profile, port, and JVM defaults.
2. Its own `<service>-runtime-config` for non-secret URLs, topics, JWT settings, and flags.
3. Its own Secret from the table above.

The ConfigMaps set `SPRING_DATASOURCE_URL` per service and use these internal endpoints:

```text
postgres:5432
redis:6379
kafka:9092
schema-registry:8081
```

## Manual now vs deferred

Manual in Phase 15:

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `RATE_LIMIT_KEY_HMAC_SECRET`
- `AUTH_THROTTLE_HMAC_SECRET`
- `CAMPAIGN_CLIENT_SECRET`
- `FLASHSALE_CLIENT_SECRET`
- `AUTH_JWT_KEY_DIR` path containing the two JWT PEM files

Deferred until Payment enablement:

- `STRIPE_SECRET_KEY`
- `STRIPE_PUBLISHABLE_KEY`
- `STRIPE_WEBHOOK_SECRET`

Optional tuning properties keep the defaults already defined by each service's `application.yml`.
They are not missing secrets.
