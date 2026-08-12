# 06 - Gateway Architecture, Package, and Build

**Document status**: Approved living design for Feature 013  
**Canonical sources**: [plan](../../specs/013-gateway-redis-rate-limiter/plan.md),
[ADR 0003](../adr/0003-lean-api-gateway-package-structure.md), [ADR 0004](../adr/0004-gateway-distributed-rate-limiter.md)

## 1. Architecture Choice

Gateway rate limiting is a technical edge capability, not a business bounded context. Feature 013
therefore keeps the ADR 0003 responsibility-oriented Gateway structure and does not create
business-service folders such as:

```text
ratelimit/domain
ratelimit/application
ratelimit/infrastructure
ratelimit/web
```

Clean boundary here means the filter does not know Redis/Lua details, Redis code does not render
HTTP, and configuration wires components without choosing behavior at runtime.

## 2. Approved Package Tree

Only create real types when their approved task needs them:

```text
com/philia/flashsale/gateway/
├── configuration/
│   ├── GatewayRateLimitConfiguration.java
│   └── GatewayRateLimitProperties.java
├── filter/global/
│   ├── CatalogCorrelationIdGlobalFilter.java
│   └── GatewayRateLimitGlobalFilter.java
├── ratelimit/
│   ├── DirectClientIpRateLimitIdentityResolver.java
│   ├── DistributedRateLimiter.java
│   ├── HmacRateLimitBucketKeyFactory.java
│   ├── RateLimitCoordinatorException.java
│   ├── RateLimitDecision.java
│   ├── RateLimitFailureType.java
│   ├── RateLimitIdentityResolver.java
│   ├── RateLimitPolicy.java
│   ├── RateLimitPolicyResolver.java
│   └── redis/
│       └── RedisTokenBucketRateLimiter.java
├── error/
│   └── GatewayHttpErrorWriter.java
└── observability/
    └── GatewayRateLimitObservation.java

src/main/resources/
└── redis/token_bucket.lua
```

Do not add placeholder classes, mapper packages, `utils`, `service/impl`, a second error writer, or
DTOs that duplicate the existing Gateway error envelope.

## 3. Dependency Direction

```text
CatalogCorrelationIdGlobalFilter
  -> existing GatewayTraceIdResolver

GatewayRateLimitGlobalFilter
  -> RateLimitPolicyResolver
  -> RateLimitIdentityResolver
  -> HmacRateLimitBucketKeyFactory
  -> DistributedRateLimiter
  -> GatewayHttpErrorWriter
  -> GatewayRateLimitObservation

RedisTokenBucketRateLimiter
  -> DistributedRateLimiter + RateLimitPolicy/Decision

GatewayRateLimitConfiguration
  -> filter + ratelimit + Redis adapter
```

Rules:

- `error` does not depend on Redis or identity internals.
- Redis adapter does not contain HTTP response logic.
- Filter does not know Lua tuple shape or Redis exception details.
- Observability records bounded outcomes; it does not decide allow/reject.
- Configuration binds, validates, and wires; it does not invent quota semantics.

## 4. Filter Ordering

`CatalogCorrelationIdGlobalFilter` runs at
`RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2`.

`GatewayRateLimitGlobalFilter` runs at order `-1`.

Spring Security `WebFilter` ordering and Spring Cloud Gateway `GlobalFilter` ordering are different
namespaces. Do not compare their numbers directly.

## 5. Maven Delta

Approved dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

No OpenTelemetry, tracing bridge, resilience, database, Kafka, MapStruct, or additional Prometheus
registry dependency is part of Feature 013.

## 6. Build Boundaries

- Gateway remains one Maven module and one deployable JAR.
- Lua resource is packaged inside the Gateway JAR.
- Shared Redis Compose topology remains under `infra/docker`.
- Gateway policy and Redis client configuration remain service-owned in `api-gateway`.
