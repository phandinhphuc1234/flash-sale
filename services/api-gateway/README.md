# API Gateway

Spring Cloud Gateway WebFlux is the single public business edge. It owns routing and cross-cutting
HTTP policy; it does not own a database or business decisions.

## Responsibilities

- route public Authentication, Product, Cart, Campaign, Flash Sale, Inventory, Order, Payment, and
  Stripe webhook paths to their owners;
- validate shopper/admin RS256 JWTs and preserve authorization headers downstream;
- allow configured credentialed CORS origins and unauthenticated browser preflight requests;
- apply Redis-backed distributed rate limits with HMAC-derived keys;
- propagate/generate trace identifiers and return the standard error envelope;
- aggregate eight service OpenAPI documents when local documentation is explicitly enabled.

Internal `/internal/**` APIs are deliberately absent from Gateway routes.

## Request path

```text
client -> CORS/security -> rate-limit filter -> trace filter -> route -> owning service
```

Readiness and liveness paths remain available to probes. Public and protected business routes keep
their owner-specific authorization rules.

## Configuration

Important environment groups:

- routing: `*_SERVICE_URL`;
- listener: `SERVER_PORT`;
- JWT trust: `JWT_ISSUER_URI`, `JWT_JWK_SET_URI`, `JWT_AUDIENCE`;
- CORS: `GATEWAY_CORS_ALLOWED_ORIGINS`;
- rate limiting: `GATEWAY_RATE_LIMIT_ENABLED`, `RATE_LIMIT_ENVIRONMENT`,
  `RATE_LIMIT_KEY_HMAC_SECRET`, and Redis connection settings;
- local docs: `API_DOCS_ENABLED` (safe default `false`).

Use an exact comma-separated origin allow-list. Do not use `*` with credentialed browser sessions.

## Run and verify

```powershell
.\mvnw.cmd -pl services/api-gateway -am verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d --build api-gateway
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

With local docs enabled, open `http://localhost:8080/swagger-ui.html`. See the
[HTTP catalog](../../docs/api/README.md) for routes and access rules.

## Troubleshooting

- Browser `Failed to fetch`: inspect the `OPTIONS` request first. A protected preflight must return
  CORS headers without requiring JWT authentication.
- `401`: distinguish a missing/invalid shopper token from a downstream service response.
- `403` for a valid browser: verify the exact scheme, host, and port in the CORS allow-list.
- `429`: inspect Redis connectivity and limiter configuration before raising limits.
- Swagger returns `401`: enable `API_DOCS_ENABLED=true` locally and rebuild/restart Gateway.
