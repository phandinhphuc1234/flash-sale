# Configuration Contract: Gateway Catalog Rate Limit

**Owner**: `api-gateway`  
**Feature**: `013-gateway-redis-rate-limiter`  
**Status**: Approved — approved by Gateway/Platform owner (user) on 2026-07-23

This is an operator-facing configuration contract, not a public HTTP API.

## 1. Service-owned properties

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

Redis connection settings continue to use Spring Boot's `spring.data.redis.*` namespace. Feature 013
does not duplicate host, port, username, password, TLS, pool, or driver settings inside its policy.

The Gateway also pins the direct-peer, health, and local measurement semantics required by this
feature:

```yaml
server:
  forward-headers-strategy: none

management:
  health:
    redis:
      enabled: false
  metrics:
    distribution:
      percentiles:
        gateway.rate.limit.acquire: 0.5,0.95,0.99
```

## 2. Global properties

| Property | Required | Default/exact rule |
|----------|----------|--------------------|
| `enabled` | No | `false`; when false, quota acquisition beans/filter are inactive and secret is not required; the separate catalog correlation filter remains active |
| `environment` | When enabled | Default `local`; 1–32 ASCII chars matching `[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?` |
| `key-prefix` | When enabled | `rl`; contains no identity or environment data |
| key schema | Code-owned | Fixed `k1`; physical format `rl:k1:<64 lowercase hex>` |
| `command-timeout` | When enabled | Exact Feature 013 value `50ms`, applied only to one Redis acquisition |
| `hmac-secret` | When enabled | Java Basic/RFC 4648 Base64 using standard `+`/`/` alphabet and legal optional `=` padding, no whitespace/URL/MIME form, decoding to at least 32 bytes; dedicated to limiter identity |
| `policies` | When enabled | Contains exactly one effective Feature 013 policy selector |

The runtime validates encoding and length, not whether bytes were genuinely random. Operators must
generate the secret with a cryptographically secure random generator. Secrets are supplied through
environment/secret management and are never committed, logged, returned, or used as metric tags.

## 3. Policy validation

- Map key is the stable policy ID and is unique.
- The only MVP policy ID is `public-catalog-read`, the only route ID is `product-catalog`, and
  `state-version` matches `p[1-9][0-9]{0,8}`.
- Two enabled policies cannot match the same `(route-id, method)`.
- Duration values are positive integral milliseconds.
- Capacity, refill tokens, and request cost are positive; request cost is no greater than capacity.
- Every numeric value passed to Lua, including scaled credit products and `refillTokens`, must fit
  Java `long` and Redis Lua's exact-integer range (`2^53 - 1`).
- Derived `fullRefillMs` must be at most `86_400_000` (24 hours), so state cannot expire and reset
  before the largest one-request `Retry-After` horizon.
- Feature 013 accepts only `product-catalog`, `GET`, `CLIENT_IP`, and `ALLOW_WITH_METRIC` for the
  deployed MVP policy.
- Missing/invalid enabled secret, unsupported enum, duplicate selector, invalid number/duration, or
  overflow fails Gateway startup before traffic is served.
- Startup validation messages identify only the property and safe reason; neither custom validation
  nor captured startup-failure logs may echo the rejected HMAC secret value.
- Invalid deployment configuration is never converted to a public HTTP error.

Tests may override capacity/refill values for deterministic fixtures, including the approved
capacity-20 concurrency case. A deployment change to quota, cost, TTL rule, or identity strategy
requires owner approval and a new `state-version`.

## 4. Docker/local environment

Root Compose reuses its existing `redis:7.4-alpine` service and passes only:

```text
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
GATEWAY_RATE_LIMIT_ENABLED=<explicit true/false>
RATE_LIMIT_ENVIRONMENT=<local/docker identifier>
RATE_LIMIT_KEY_HMAC_SECRET=<operator-provided Base64>
PRODUCT_LIQUIBASE_ENABLED=<true only for the deterministic local load fixture>
```

The Product Compose service maps that opt-in explicitly as
`SPRING_LIQUIBASE_ENABLED: ${PRODUCT_LIQUIBASE_ENABLED:-false}`; the Gateway does not consume it.

Compose defaults the feature to disabled unless the developer explicitly enables it. The repository
does not commit a real local secret and does not add a Redis service, volume, network, database, or
service-local Compose file. The Gateway service explicitly overrides the shared app anchor's
`depends_on` with `{}`. `PRODUCT_LIQUIBASE_ENABLED=true` only applies Product's existing schema so
the documented empty-catalog load target can run; it does not add or seed Product data.

## 5. Health and failure semantics

`management.health.redis.enabled=false` prevents Redis from changing Gateway aggregate health or
liveness/readiness for this fail-open policy. An enabled limiter with a runtime Redis outage keeps
routing through the typed fail-open branch and emits bounded metrics/Observation. Logs are limited
to safe lifecycle/configuration transitions and never emitted per failed request. Root Compose must
override the Gateway's inherited backing-service `depends_on` with `{}` so Redis/PostgreSQL/Kafka
startup health does not block the stateless edge process.

This does not hide Redis degradation: Feature 013 metrics and failure-injection evidence remain
required. It prevents Kubernetes/Compose health routing from silently turning HTTP fail-open into a
deployment-level fail-closed outcome.

`server.forward-headers-strategy=none` is required for the direct-peer MVP. Incoming `Forwarded` and
`X-Forwarded-For` values therefore cannot rewrite the address used by the limiter. A trusted-proxy
strategy requires the deferred Kubernetes feature.

## 6. Secret and state-version rollout

- All active Gateway replicas must share the same environment, HMAC secret, key schema, policy ID,
  and policy-state version.
- Secret rotation or material policy-state change creates a fresh bucket namespace.
- Old keys expire by TTL; no scan, migration, dual-read, or manual delete is performed.
- For this local MVP, disable the limiter before changing secret/state version and re-enable only
  after all replicas share the new configuration.
- A future Kubernetes production rollout must define trusted ingress identity, secret distribution,
  coordinated/dual-version activation, and Redis HA in a separately approved feature/ADR.

## 7. Dependency contract

The Gateway adds:

- production: `spring-boot-starter-data-redis-reactive`;
- production: `spring-boot-starter-validation`;
- test: `org.testcontainers:junit-jupiter`.

Versions remain BOM-managed by the repository. Feature 013 adds no OpenTelemetry, tracing bridge,
resilience, database, Kafka, or additional Prometheus registry dependency.
