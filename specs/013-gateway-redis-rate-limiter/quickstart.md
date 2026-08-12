# Quickstart and Verification: Gateway Redis Rate Limiter

**Feature**: `013-gateway-redis-rate-limiter`  
**Status**: Supporting execution guide — governed by `plan.md`/`tasks.md` approval

> All behavior decisions are resolved and the governing artifacts are approved/accepted. Commands
> become executable gates only when the corresponding approved task is being implemented.

## 1. Prerequisites

- Java 21
- Repository Maven wrapper
- Docker Desktop/Engine capable of running Testcontainers
- Docker Compose v2
- a local k6 binary for the measurement exercise; record `k6 version`

No PostgreSQL migration, Kafka topic, second Redis, Kubernetes cluster, OpenTelemetry Collector, or
Tempo instance is required to implement/test the limiter itself.

## 2. Generate a local HMAC secret

Generate at least 32 random bytes and encode them as standard Base64. PowerShell example:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Place the output in an uncommitted `infra/docker/.env` as
`RATE_LIMIT_KEY_HMAC_SECRET=<output>`. Do not paste it into YAML, source code, tests, logs, screenshots,
issues, or this feature directory. Tests use their own deterministic test-only secret.

## 3. Validate and start the existing Redis

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d redis
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps redis
```

Expected topology: exactly the existing `redis:7.4-alpine` service and `redis-data` volume. Feature
013 must not create another Redis container, network, or service-local Compose file.

## 4. Default-disabled smoke check

Without `GATEWAY_RATE_LIMIT_ENABLED=true`, the Gateway must start without a limiter secret/Redis
acquisition and preserve its current routes, security, health, status/body, and error behavior. The
intentional exception is the always-active matched-catalog `X-Trace-Id` downstream propagation.

Focused test after implementation:

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=GatewayRateLimitDisabledTests `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: limiter/acquisition bean is inactive, catalog requests follow existing routing with one
normalized/generated downstream `X-Trace-Id`, and liveness/readiness remain unchanged.

## 5. Focused unit/security tests

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=GatewayRateLimitPropertiesTests,RateLimitPolicyResolverTests,DirectClientIpRateLimitIdentityResolverTests,HmacRateLimitBucketKeyFactoryTests `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected evidence:

- exact route/method/policy selection;
- enabled invalid policy/secret startup rejection;
- every numeric Lua argument, including `refillTokens`, is bounded by `2^53 - 1`, and full-refill
  duration above 24 hours is rejected;
- disabled configuration accepts no secret;
- forwarding headers ignored;
- deterministic IPv4/IPv6 normalization;
- 64 lowercase hexadecimal HMAC output and namespace separation;
- changing the HMAC secret changes the namespace;
- raw identity/secret absent from rendered objects/log capture, including enabled-startup validation
  failure with a distinctive secret sentinel.

## 6. Real Redis correctness and concurrency

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=RedisTokenBucketRateLimiterTests `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The test starts its own Redis 7.4 Testcontainer. It must never connect to or flush developer Compose
Redis.

Required evidence:

- missing state starts full;
- allowed cost, refill, capacity clamp, and retry delay use integer credit correctly;
- allowed acquisition refreshes 60-second TTL;
- rejected acquisition does not increase `PTTL`;
- stable positive finite TTL remains valid; approved wrong-type/no-TTL expiry-only normalization plus
  partial, non-canonical/non-numeric and future timestamp cases return the stable typed state failure;
- a no-TTL otherwise-valid hash fails open once and may resume normal evaluation next time, while
  still-invalid content never refreshes the attached expiry;
- a structurally valid but exhausted hash with a positive TTL shorter than its exact retry horizon
  returns `INVALID_STATE`, preserves state/expiry, and fail-opens without `Retry-After`;
- largest accepted exact integer round-trips in canonical non-exponent decimal form; leading zero,
  sign, fraction, exponent, non-finite, and out-of-range forms are rejected before mutation;
- script-cache flush recovers through Spring Data `EVALSHA`/`EVAL` behavior;
- 100 same-bucket calls through three independent clients at capacity 20 yield exactly 20 allowed and
  80 rejected, with no negative balance.

Deterministic timeout/malformed-result mapping is a unit test, not a slow or corrupted real Redis
fixture:

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=RedisTokenBucketRateLimiterFailureTests `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Reactor virtual time makes `Flux.never()` reach the exact 50 ms timeout with one
subscription/no retry; direct recognized types, `RedisSystemException` with only an immediate
recognized Lettuce cause, `QueryTimeoutException` only with immediate `RedisCommandTimeoutException`,
and near-miss wrappers prove the finite mapping without message parsing or broad catches. Real Redis/cache-recovery
fixtures use the exact test-only 2-second timeout, while production configuration remains exactly
50 ms. Unexpected programming errors propagate to the existing error boundary.

## 7. HTTP, failure, health, and observability contracts

```powershell
.\mvnw.cmd -pl services/api-gateway -am `
  -Dtest=CatalogCorrelationIdGlobalFilterTests,GatewayRateLimitGlobalFilterTests,GatewayRateLimitContractTests,GatewayRateLimitFailureTests,GatewayHttpErrorWriterTests,GatewayErrorCodeTests,GatewayProxyPassThroughTests,GatewayRateLimitObservationTests,GatewayRateLimitDisabledTests `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected evidence:

- Gateway-owned 429 has exact three fields, positive integer `Retry-After`, and
  `Cache-Control: no-store`;
- no accounting headers on allowed/rejected/fail-open responses;
- rejected request calls downstream zero times;
- allowed, identity-unavailable, timeout, connection, invalid-state/result fail-open calls downstream
  exactly once;
- timeout/unknown execution is not retried;
- downstream 429 bytes and sentinel headers pass through unchanged;
- serialization fallback 500 carries no leftover quota headers;
- expected Gateway-owned 429 creates no generic per-error WARN, while serialization fallback records
  the central error observation exactly once and other Gateway errors retain their prior behavior;
- Redis outage leaves aggregate health, liveness and readiness independent of Redis;
- metrics/Observation fields use only bounded dimensions and contain no IP, secret, digest/key,
  query, or direct OpenTelemetry type; limiter outcomes create no per-request INFO/WARN log;
- catalog correlation order is exactly `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2` and
  limiter order is exactly `- 1`; matched route ID owns both. A valid caller trace is normalized and
  propagated, missing/invalid public trace gets one fallback shared by downstream/owned-429 paths,
  disabled limiting still propagates it, and strict admin validation is unchanged;
- incoming forwarded-origin transformation is disabled, so spoofed `Forwarded` and
  `X-Forwarded-For` values cannot replace the direct peer.

## 8. Full Gateway and repository gates

```powershell
.\mvnw.cmd -pl services/api-gateway -am verify
.\mvnw.cmd clean verify
```

Both must exit 0 before completion. Record exact commands, timestamp, environment, test/Redis image,
exit code, and CI/PR reference in `specs/013-gateway-redis-rate-limiter/validation.md` during
implementation. A checked task is not a substitute for evidence.

## 9. Explicit local Docker activation

In uncommitted `infra/docker/.env`:

```dotenv
GATEWAY_RATE_LIMIT_ENABLED=true
RATE_LIMIT_ENVIRONMENT=docker
RATE_LIMIT_KEY_HMAC_SECRET=<generated-standard-Base64>
PRODUCT_LIQUIBASE_ENABLED=true
```

Start the required application topology using the root Compose files. Gateway must connect to host
`redis` on port `6379`; no localhost Redis address is valid from inside the Gateway container.

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d --build redis api-gateway product-service
```

`PRODUCT_LIQUIBASE_ENABLED=true` applies the existing Product schema only; no seed is needed. The
measurement endpoint `GET /api/v1/catalog/products?page=0&size=20` returns a valid empty catalog page
before quota exhaustion.

Do not start k6 until both checks return HTTP 200 (repeat manually while containers initialize):

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/api/v1/catalog/products?page=0&size=20'
```

Prove orchestration does not turn fail-open into a startup dependency:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml stop redis
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps stop api-gateway
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d --no-deps api-gateway
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps api-gateway
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health/liveness
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health/readiness
```

Expected: rendered Gateway `depends_on` is empty, the Gateway process starts, and aggregate health,
liveness and readiness return HTTP 200 while Redis remains stopped. Retry the HTTP probes manually
while Gateway initializes. Redis-down request behavior is proved by deterministic failure tests;
restore Redis before module/full verification and the load exercise.

Restore a healthy topology before module/full verification and load measurement:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d redis product-service api-gateway
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
```

Disable immediately without deleting Redis state:

```dotenv
GATEWAY_RATE_LIMIT_ENABLED=false
```

Then recreate/restart only Gateway and verify catalog routing. Old bucket keys expire naturally.

## 10. Measurement-only k6 exercise

With Gateway, Redis, and the migrated empty Product catalog running, wait at least the 60-second state
TTL after any earlier sample (or use a newly generated local-only secret), then recreate Gateway to
reset cumulative Micrometer percentiles. Check only health after recreation—do not send a catalog
request that would consume the fresh bucket before k6:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d --force-recreate --no-deps api-gateway
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
```

Then run:

```powershell
k6 run -e BASE_URL=http://localhost:8080 load-tests/k6/scenarios/gateway-rate-limit.js
```

The script targets exactly `GET /api/v1/catalog/products?page=0&size=20` from one local k6 process.
Only 200 and Gateway-owned 429 are accepted, and the burst phase must observe both. Redis outage is
not simulated here; Maven failure tests own outage correctness.

Capture the limiter decision timer separately from k6's HTTP latency:

```powershell
(Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/prometheus).Content |
  Select-String 'gateway_rate_limit_acquire_seconds'
```

Capture Redis signals before/after or during the run:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml exec -T redis redis-cli INFO stats
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml exec -T redis redis-cli INFO memory
```

The report separates k6 Gateway HTTP throughput and HTTP p50/p95/p99 from Prometheus acquisition
p50/p95/p99, then adds allowed/rejected counts, status/rejection accuracy, transport/setup errors,
and Redis stats/error/resource signals. This run has no production throughput threshold. It passes
planning expectations when the scenario completes, both 200/429 are present in the burst, required
measurements exist, and no setup/transport error invalidates the sample. Store commands, versions,
profile, and results in implementation `validation.md`.

## 11. Troubleshooting outcomes

| Symptom | Expected interpretation/action |
|---------|--------------------------------|
| Startup fails with limiter enabled | Check Base64 validity, decoded length ≥32, policy selector/numbers/state version |
| Redis unavailable but catalog still routes | Expected `ALLOW_WITH_METRIC`; inspect bounded failure signal; no 429 expected |
| Catalog returns owned 429 | Successful bucket exhaustion; inspect `Retry-After`; downstream must not be called |
| Downstream returns its own 429 | Body/headers must remain downstream-owned; do not diagnose as Gateway quota without signals |
| Readiness goes down only because Redis is down | Regression; Redis is not an MVP readiness dependency |
| Rate resets after Redis loss/secret/state-version change | Accepted ephemeral-state consequence; verify change was coordinated |

## 12. Kubernetes expansion path (not part of Feature 013 implementation)

Do not enable this direct-IP policy behind Kubernetes ingress yet. First create a separate approved
feature/ADR that defines:

1. trusted proxy/ingress CIDRs or exact hop selection for `Forwarded`/`X-Forwarded-For`;
2. Kubernetes Secret or approved external-secret distribution and coordinated rotation;
3. Redis HA/Cluster, credentials/TLS, resource requests/limits, persistence/eviction, and failure SLO;
4. multi-replica policy-state rollout without temporarily granting parallel capacity;
5. root `infra/monitoring` dashboards/alerts and actual Micrometer Tracing/OpenTelemetry runtime;
6. Kustomize/Helm ownership and `kubectl apply --dry-run=client -k <overlay>` validation.

Feature 013 deliberately supplies the versioned key, conditional flag, Micrometer boundary, and
root-infrastructure separation needed for that later work without pretending those production
decisions are already complete.
