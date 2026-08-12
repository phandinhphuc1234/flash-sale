# 11 - Local Runtime and Kubernetes Expansion

**Document status**: Approved living design for Feature 013 local runtime; Kubernetes production
activation is deferred.

## 1. Local Runtime

Root Compose already owns Redis:

```text
service: redis
image: redis:7.4-alpine
container port: 6379
host port: ${REDIS_HOST_PORT:-6379}
volume: redis-data
network: flash-sale-net
```

Feature 013 reuses this service. Do not add `gateway-redis`, a Gateway-local Compose file, a second
volume, or a second network.

## 2. Connection Ownership

| Runtime | Redis host | Port |
|---------|------------|------|
| Gateway from IDE/Maven | `localhost` | `${REDIS_HOST_PORT:-6379}` |
| Gateway container in Compose | `redis` | `6379` |
| Integration tests | Testcontainer host | Random mapped port |
| Future Kubernetes | Kubernetes Service DNS or approved managed Redis endpoint | Service port |

Redis host, port, credentials, TLS, and pooling use `spring.data.redis.*`. Rate-limit policy owns
quota semantics, not connection topology.

## 3. Compose Variables

Root Compose passes:

```text
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
GATEWAY_RATE_LIMIT_ENABLED=<explicit true/false>
RATE_LIMIT_ENVIRONMENT=<local/docker identifier>
RATE_LIMIT_KEY_HMAC_SECRET=<operator-provided Base64>
PRODUCT_LIQUIBASE_ENABLED=<true only for deterministic local load fixture>
```

Product maps the opt-in exactly as:

```text
SPRING_LIQUIBASE_ENABLED: ${PRODUCT_LIQUIBASE_ENABLED:-false}
```

Do not commit real Redis credentials or HMAC secrets.

## 4. Enablement and Health

- Base configuration is default disabled.
- Enabled invalid policy/secret fails startup.
- `management.health.redis.enabled=false` keeps Gateway health, liveness, and readiness independent
  of Redis under the approved fail-open policy.
- Root Compose must render Gateway `depends_on: {}` so Redis/PostgreSQL/Kafka startup health does
  not block the stateless Gateway process.

Redis degradation is still visible through limiter metrics/Observation and failure tests.

## 5. Rollback

1. Set `GATEWAY_RATE_LIMIT_ENABLED=false`.
2. Restart or redeploy Gateway.
3. Verify catalog routing and existing Gateway error behavior.
4. Do not flush Redis or delete the shared volume.
5. Let limiter keys expire naturally.

## 6. Kubernetes Boundary

Do not enable the direct-IP policy behind Kubernetes ingress in Feature 013. A later approved
feature/ADR must define:

- trusted ingress/proxy CIDRs or hop strategy;
- Secret or external-secret distribution and coordinated rotation;
- Redis HA/Cluster or managed Redis topology, credentials/TLS, and resource limits;
- multi-replica rollout without temporarily granting parallel capacity;
- NetworkPolicy and Redis access ownership;
- root `infra/monitoring` dashboards, alerts, and tracing runtime assets;
- Kustomize/Helm ownership and `kubectl apply --dry-run=client -k <overlay>` validation.

Gateway image still owns its Lua resource; root infrastructure must not copy or own application
scripts.
