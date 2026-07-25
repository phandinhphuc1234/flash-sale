# 03 - Policy and Configuration

**Document status**: Approved living design for Feature 013  
**Canonical contract**: [Gateway Catalog Rate Limit Configuration](../../specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-configuration.md)

## 1. Policy Principles

- Policy is service-owned Gateway configuration.
- A request resolves at most one effective policy.
- Feature 013 matches only stable route ID plus HTTP method.
- No policy means no distributed quota acquisition.
- No client-provided bypass header exists.
- Quota, TTL, timeout, identity, and failure semantics are approved feature behavior, not examples.

## 2. Approved Service Configuration

```yaml
flashsale:
  gateway:
    rate-limit:
      enabled: ${GATEWAY_RATE_LIMIT_ENABLED:false}
      environment: ${RATE_LIMIT_ENVIRONMENT:local}
      key-prefix: rl
      command-timeout: 50ms
      hmac-secret: ${RATE_LIMIT_KEY_HMAC_SECRET:}
      policies:
        public-catalog-read:
          state-version: p1
          route-id: product-catalog
          methods: [GET]
          identity-strategy: CLIENT_IP
          capacity: 60
          refill-tokens: 30
          refill-period: 1s
          request-cost: 1
          failure-mode: ALLOW_WITH_METRIC
          enabled: true
```

Redis connection settings stay in Spring Boot's `spring.data.redis.*` namespace. The rate-limit
policy does not duplicate Redis host, port, password, TLS, or pool settings.

## 3. Global Parameters

| Parameter | Approved rule |
|-----------|---------------|
| `enabled` | Defaults to `false`; when false, quota acquisition is inactive and no secret is required. |
| `environment` | 1-32 ASCII chars matching `[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?`. |
| `key-prefix` | Exact MVP value `rl`; contains no identity or environment plaintext. |
| Key schema | Code-owned fixed value `k1`; physical Redis key is `rl:k1:<64 lowercase hex>`. |
| `command-timeout` | Exact value `50ms` around one Redis acquisition. |
| `hmac-secret` | Required only when enabled; standard Base64, at least 32 decoded bytes, dedicated to limiter identity. |
| `policies` | Exactly one effective MVP policy selector when enabled. |

## 4. Per-Policy Parameters

| Parameter | Approved value/rule |
|-----------|---------------------|
| Policy ID | `public-catalog-read`. |
| `state-version` | `p1`, matching `p[1-9][0-9]{0,8}`. |
| `route-id` | `product-catalog`. |
| `methods` | `[GET]`. |
| `identity-strategy` | `CLIENT_IP`. |
| `capacity` | `60`. |
| `refill-tokens` | `30`. |
| `refill-period` | `1s`, exact positive milliseconds. |
| `request-cost` | `1`, positive and no greater than capacity. |
| `failure-mode` | `ALLOW_WITH_METRIC`. |
| `enabled` | `true` under the global flag. |

Tests may override quota values for deterministic fixtures, including capacity `20` for the
approved concurrency test. Deployment policy changes require owner approval and a new
`state-version` when quota, TTL, request cost, or identity semantics materially change.

## 5. Startup Validation

Gateway startup must fail before serving traffic when the limiter is enabled and configuration is
invalid:

- missing, malformed, or too-short HMAC secret;
- duplicate enabled `(route-id, method)` selector;
- unsupported route, method, identity strategy, or failure mode;
- invalid policy/environment/state-version naming;
- non-positive capacity/refill/period/cost;
- request cost greater than capacity;
- numeric overflow or any Lua numeric argument above `2^53 - 1`;
- derived full-refill duration above `86_400_000` ms.

Invalid deployment configuration is not converted into a public HTTP error.

## 6. Local Docker Variables

Root Compose reuses the existing Redis service and passes only:

```text
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
GATEWAY_RATE_LIMIT_ENABLED=<explicit true/false>
RATE_LIMIT_ENVIRONMENT=<local/docker identifier>
RATE_LIMIT_KEY_HMAC_SECRET=<operator-provided Base64>
PRODUCT_LIQUIBASE_ENABLED=<true only for deterministic local load fixture>
```

Product maps the load-fixture opt-in exactly as
`SPRING_LIQUIBASE_ENABLED: ${PRODUCT_LIQUIBASE_ENABLED:-false}`. Gateway does not consume that
variable.
