# Flash Sale Engine — Current System Implementation

## Document snapshot

| Field | Value |
|---|---|
| Repository | `phandinhphuc1234/flash-sale` |
| Branch | `develop` |
| Base commit | `7f2a30f` |
| Snapshot date | 2026-07-29 |
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 |
| Build system | Maven Wrapper, multi-module reactor |
| Snapshot source | Current working tree, including uncommitted and untracked implementation |

### Status vocabulary

| Status | Meaning in this document |
|---|---|
| Implemented | Production source exists for the capability |
| Verified | The listed validation command passed on this snapshot |
| Partial | A working slice exists, but required behavior or verification remains |
| Scaffold | Runnable Spring Boot module only; no business API or domain implementation |
| Configured | Dependency/runtime configuration exists without the complete business capability |
| Deferred | Explicitly left for a later approved feature |
| Planned only | Documentation or an empty reserved directory exists; no runtime implementation |

> **API documentation update (2026-08-29):** The former snapshot below predates the unified API
> documentation feature. The current HTTP source of truth is [docs/api/README.md](api/README.md):
> it inventories 40 supported method/path pairs and documents seven opt-in service OpenAPI
> documents. Keep Swagger disabled on cloud and enable it only through local Compose when needed.

## 1. Current system summary

| Area | Current state |
|---|---|
| Maven reactor | Root parent plus `common-web` and 10 independently packaged Spring Boot services |
| Public edge | Spring Cloud Gateway WebFlux with Auth, Product, and Inventory routes |
| Authentication | Registration, login, RS256 JWT/JWKS, rotating refresh cookie, logout, throttling, cleanup |
| Product | Public catalog query plus privileged Product administration, composition, lifecycle, idempotency, audit |
| Inventory | Physical stock, immutable movements, campaign allocations, outbox persistence, admin/internal HTTP APIs |
| Remaining services | Cart, Campaign, Flash Sale, Order, Payment, and Notification remain runnable scaffolds |
| Durable data | PostgreSQL schemas implemented for Authentication, Product, and Inventory |
| Redis | Gateway distributed token-bucket limiter and Authentication login throttle |
| Kafka | Broker exists in local Compose; no producer, consumer, Kafka dependency, or promoted event contract is implemented |
| HTTP communication | Gateway reverse proxy is implemented; no business service outbound HTTP client is implemented |
| Metrics | Actuator and Prometheus registry configured in every service |
| Distributed tracing | Not implemented; custom `X-Trace-Id` correlation exists in Gateway/Auth/Product paths |
| OpenAPI | Seven HTTP-owning services; local opt-in and disabled by default |
| Containers | One multi-stage, non-root Dockerfile per service; shared local Compose under `infra/docker` |
| Kubernetes | Documentation/reserved directory only; no manifests |
| Monitoring stack | Documentation/reserved directory only; no Prometheus server, Grafana, Tempo, Loki, or OTel Collector |

## 2. Repository layout

```text
flash-sale/
├── .agents/
│   └── skills/
│       ├── apply-clean-hex-architecture/
│       └── spring-clean-feature-architecture/
├── .specify/
│   ├── memory/constitution.md
│   └── feature.json
├── contracts/
│   ├── asyncapi/                         # reserved, empty
│   ├── openapi/                          # reserved, empty
│   └── events/                           # reserved, empty domain folders
├── docs/
│   ├── adr/
│   ├── architecture/
│   ├── authen/
│   ├── database/
│   ├── deployment/
│   ├── inventory/
│   ├── product/
│   ├── ratelimit/
│   ├── spec-driven-development/
│   └── technology/
├── infra/
│   ├── docker/                           # implemented local topology
│   ├── helm/                             # README only
│   ├── k8s/                              # README only
│   └── monitoring/                       # README only
├── libs/
│   ├── common-web/                       # implemented Maven module
│   ├── kafka-support/                    # empty reserved directory
│   ├── observability/                    # empty reserved directory
│   ├── security-jwt/                     # empty reserved directory
│   └── test-support/                     # empty reserved directory
├── load-tests/
│   └── k6/                               # empty reserved directories
├── services/
│   ├── api-gateway/
│   ├── authentication-service/
│   ├── product-service/
│   ├── cart-service/
│   ├── campaign-service/
│   ├── flashsale-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── notification-service/
│   └── inventory-service/
├── specs/
│   └── 001 ... 016
├── AGENTS.md
├── README.md
├── mvnw / mvnw.cmd
└── pom.xml
```

## 3. Root platform and build baseline

### 3.1 Maven modules

```text
libs/common-web
services/api-gateway
services/authentication-service
services/product-service
services/cart-service
services/campaign-service
services/flashsale-service
services/order-service
services/payment-service
services/notification-service
services/inventory-service
```

### 3.2 Managed versions

| Item | Version/source |
|---|---|
| Java release | `21` |
| Spring Boot parent | `3.5.16` |
| Spring Cloud BOM | `2025.0.3` |
| MapStruct property | `1.6.3` |
| Project version | `0.1.0-SNAPSHOT` |

### 3.3 Repository architecture rules in force

- External traffic enters through `api-gateway`.
- Each service owns its source, configuration, tests, database, and Liquibase changelog.
- Services do not query another service's database.
- PostgreSQL is durable truth.
- Redis is limited to cache, coordination, throttling, and approved hot paths.
- Kafka contracts must be versioned before producer/consumer implementation.
- Durable state plus required event publication uses an outbox where applicable.
- Synchronous service integration uses documented HTTP contracts.
- Kubernetes Service/DNS is the selected future discovery mechanism; Eureka is absent.
- Domain code is not shared across service boundaries.
- JPA entities and repositories are service-local.
- Service code exposes Actuator health, liveness, readiness, info, and Prometheus endpoints declaratively.
- No Java code constructs a `PrometheusMeterRegistry`.

## 4. Shared library implementation

### 4.1 `libs/common-web`

Status: implemented Maven JAR, no Spring dependency, no tests.

```text
com.philia.flashsale.common.web/
├── ApiResponse.java
├── ApiErrorResponse.java
├── FieldViolation.java
├── PageMeta.java
└── PageResponse.java
```

| Type | Current transport shape |
|---|---|
| `ApiResponse<T>` | `success`, `code`, `message`, `data`, `timestamp` |
| `ApiErrorResponse` | `success`, `errorCode`, `message`, optional `errors`, `timestamp` |
| `FieldViolation` | `field`, `message` |
| `PageResponse<T>` | `data`, `page` |
| `PageMeta` | `number`, `size`, `totalElements`, `totalPages`, `hasNext` |

Current consumers:

- `product-service`: shared envelopes plus `PageResponse`/`PageMeta`; item DTOs remain feature-local.
- `inventory-service`: shared success, error, validation, and pagination types.
- `api-gateway`: shared `ApiErrorResponse` for Gateway-owned failures; downstream bodies pass through.
- `authentication-service`: shared success/error envelopes; token and cookie DTOs remain feature-local.

### 4.2 Reserved shared-library directories

| Directory | Current state |
|---|---|
| `libs/security-jwt` | Empty; no POM or production source |
| `libs/kafka-support` | Empty; no POM or production source |
| `libs/observability` | Empty; no POM or production source |
| `libs/test-support` | Empty; no POM or test utilities |

## 5. API Gateway

### 5.1 Module status

| Item | State |
|---|---|
| Runtime | Spring Cloud Gateway Server WebFlux |
| Source files | 27 production Java files |
| Test classes | 23 |
| Database | None |
| Redis | Reactive Redis token-bucket coordinator |
| JWT | OAuth2 Resource Server with remote JWKS |
| Business domain | None; technical edge module |
| Docker image | Multi-stage Java 21 build, non-root runtime |

### 5.2 Package structure

```text
com.philia.flashsale.gateway/
├── ApiGatewayApplication.java
├── configuration/
│   ├── GatewayRateLimitConfiguration.java
│   └── GatewayRateLimitProperties.java
├── error/
│   ├── GatewayErrorCode.java
│   ├── GatewayFailureClassifier.java
│   ├── GatewayHttpErrorWriter.java
│   └── GatewayWebExceptionHandler.java
├── filter/global/
│   ├── AdminCatalogRequestBoundaryFilter.java
│   ├── AuthenticationCorrelationIdGlobalFilter.java
│   ├── CatalogCorrelationIdGlobalFilter.java
│   └── GatewayRateLimitGlobalFilter.java
├── observability/
│   ├── GatewayErrorObservation.java
│   └── GatewayTraceIdResolver.java
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
│   └── redis/RedisTokenBucketRateLimiter.java
└── security/
    ├── GatewayJwtTrustConfiguration.java
    ├── GatewaySecurityConfiguration.java
    └── GatewaySecurityErrorHandler.java
```

Runtime resource:

```text
src/main/resources/redis/token_bucket.lua
```

### 5.3 Configured routes

| Route ID | Method/path | Downstream default |
|---|---|---|
| `authentication-login` | `POST /api/v1/auth/login` | `http://authentication-service:8080` |
| `authentication-refresh` | `POST /api/v1/auth/refresh` | `http://authentication-service:8080` |
| `authentication-api` | `/api/v1/auth/**` | `http://authentication-service:8080` |
| `product-catalog-admin` | `/api/v1/admin/catalog/**` | `http://product-service:8080` |
| `product-catalog` | `/api/v1/catalog/**` | `http://product-service:8080` |
| `inventory-admin` | `/api/v1/admin/inventory/**` | `http://inventory-service:8080` |

No Gateway route exists for Cart, Campaign, Flash Sale, Order, Payment, Notification, or Inventory internal allocation APIs.

### 5.4 CORS

- Credentialed CORS is enabled.
- Default allowed origin: `http://localhost:3000`.
- Allowed methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.
- All request headers are allowed.
- Runtime origin source: `GATEWAY_CORS_ALLOWED_ORIGINS`.

### 5.5 Gateway authorization

| Boundary | Rule |
|---|---|
| Public catalog | Permit `/api/v1/catalog/**` |
| Registration/login/refresh/current logout | Permit exact Auth paths |
| Logout all | Authenticated JWT required |
| Product administration | `CATALOG_ADMIN` required |
| Inventory administration | `INVENTORY_ADMIN` required |
| Selected exact Actuator paths | Permitted |
| Any other exchange | Denied |

JWT validation currently enforces:

- Remote JWKS URL.
- Issuer.
- Audience `flash-sale-api` by default.
- Non-blank subject.
- JOSE header `typ=at+jwt`.
- Normal JWT signature and time validation.
- Authorities from `authorities` and `roles` claims.
- OAuth scopes from `scope` and `scp`, normalized to `SCOPE_*`.

### 5.6 Correlation handling

- `GatewayTraceIdResolver` uses or generates an `X-Trace-Id` value.
- Public Product catalog GET requests receive normalized correlation before rate limiting.
- Authentication routes receive normalized `X-Trace-Id` before proxying.
- Product admin requests require a non-blank `X-Trace-Id` no longer than 128 characters.
- Product admin boundary removes caller-supplied `X-Actor-Id`.
- Current correlation is not Micrometer/OpenTelemetry distributed tracing.

### 5.7 Gateway-owned error contract

```json
{
  "success": false,
  "errorCode": "GATEWAY_ERROR_CODE",
  "message": "Client-safe message",
  "errors": null,
  "timestamp": "2026-08-01T00:00:00Z"
}
```

Correlation is returned in the `X-Trace-Id` response header; Gateway-owned JSON never contains
`traceId`. Downstream-owned responses are passed through byte-for-byte and remain owned by the
downstream service.

| Error code | HTTP status |
|---|---:|
| `INVALID_ADMIN_REQUEST` | 400 |
| `UNAUTHENTICATED` | 401 |
| `CATALOG_ADMIN_REQUIRED` | 403 |
| `INVENTORY_ADMIN_REQUIRED` | 403 |
| `ACCESS_DENIED` | 403 |
| `RATE_LIMIT_EXCEEDED` | 429 |
| `DOWNSTREAM_UNAVAILABLE` | 503 |
| `AUTHENTICATION_UNAVAILABLE` | 503 |
| `GATEWAY_INTERNAL_ERROR` | 500 |

Additional behavior:

- Gateway-owned 429 includes `Retry-After` rounded up to at least one second.
- Gateway-owned 429 includes `Cache-Control: no-store`.
- Generic Gateway errors are observed with bounded code/status/method/path/cause fields.
- An already committed downstream response is not overwritten.
- Approved connect/DNS/premature-close failures before a downstream response map to 503.
- Unknown/unclassified uncommitted failures map to 500.
- Downstream response bodies and statuses are passed through instead of being rewrapped.

### 5.8 Distributed rate limiter

Status: implemented MVP slice, disabled by default.

Implementation:

- Redis Lua token bucket.
- Redis server `TIME` for coordinator time.
- Reactive Redis client.
- Route ID plus HTTP method policy selection.
- HMAC-SHA-256 bucket keys.
- Direct socket IP identity.
- Forwarded IP headers are not trusted.
- Per-policy state version.
- Conditional beans; disabled mode does not require Redis at startup.
- Typed `RateLimitCoordinatorException` failures fail open in the global filter.

Configured policy presets:

| Policy | Selector | Capacity/refill | Failure mode |
|---|---|---|---|
| `public-catalog-read` | `GET`, route `product-catalog` | capacity 60; 30 tokens/second | `ALLOW_WITH_METRIC` |
| `auth-login` | `POST`, route `authentication-login` | capacity 10; 10 tokens/minute | `ALLOW_WITH_METRIC` |
| `auth-refresh` | `POST`, route `authentication-refresh` | capacity 30; 30 tokens/minute | `ALLOW_WITH_METRIC` |

Runtime requirements when enabled:

- Redis host/port/password.
- Standard Base64 HMAC secret decoding to at least 32 bytes.
- Environment identifier.
- Redis command timeout fixed at 50 ms by the approved preset.

Current rate-limit omissions:

- No Micrometer rate-limit observation component for the full Feature 013 telemetry contract.
- The local Redis-down fail-open/fail-closed recovery smoke passed, but the broader invalid-state,
  load, and exhaustive observability slices from Feature 013 remain incomplete.
- No load-test implementation under `load-tests/k6`.
- Feature 013 task ledger remains partially unchecked despite the simplified identity/HMAC implementation in source.

### 5.9 Gateway tests

Covered test groups:

- Application context and route configuration.
- Admin Product route security.
- Auth failure classification.
- Downstream unavailable classification.
- Unexpected failure handling.
- Unknown-path deny-by-default behavior.
- Proxy pass-through for method/path/query/body/headers/response.
- JWT issuer/audience/type validation.
- Trace ID normalization.
- Error writer and serialization fallback.
- Rate-limit policy validation.
- HMAC and direct-IP identity.
- Redis token-bucket execution.
- Global filter allowed/rejected behavior.

Current verification result:

```text
.\mvnw.cmd clean verify
Gateway tests: 177
Full reactor tests: 257
Failures: 0
Errors: 0
Result: BUILD SUCCESS
```

`GatewayErrorCodeTests` now covers all nine Gateway-owned error codes, including
`INVENTORY_ADMIN_REQUIRED` (`403`).

## 6. Authentication Service

### 6.1 Module status

| Item | State |
|---|---|
| Business implementation | Implemented MVP |
| Source files | 90 production Java files |
| Test classes | 18 |
| Web runtime | Spring MVC |
| Database | PostgreSQL `auth_db` |
| Redis | Login throttle |
| Password hashing | Configurable Argon2id |
| Access tokens | RS256 JWT |
| Refresh credentials | Opaque, HttpOnly cookie, SHA-256 digest in PostgreSQL |
| OAuth/OIDC authorization server | Not implemented |
| Automatic signing-key rotation | Not implemented |

### 6.2 Feature-oriented package structure

```text
com.philia.flashsale.authentication/
├── AuthenticationServiceApplication.java
├── account/
│   ├── domain/
│   │   ├── Account.java
│   │   ├── AccountFailure.java
│   │   ├── AccountRole.java
│   │   └── AccountStatus.java
│   ├── application/registration/
│   │   ├── RegisterAccountCommand.java
│   │   ├── RegisterAccountResult.java
│   │   ├── RegisterAccountUseCase.java
│   │   ├── RegisterAccountService.java
│   │   ├── RegisterAccountPort.java
│   │   └── EncodePasswordPort.java
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
├── session/
│   ├── domain/
│   │   ├── LoginSession.java
│   │   ├── LoginSessionStatus.java
│   │   ├── RefreshCredential.java
│   │   └── SessionFailure.java
│   ├── application/
│   │   ├── login/
│   │   ├── refresh/
│   │   └── logout/
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
├── cleanup/
│   ├── application/
│   └── adapter/in/scheduling/
├── security/
│   ├── jwks/
│   ├── password/
│   └── token/
├── throttle/redis/
├── observability/
├── websupport/
│   ├── context/
│   ├── error/
│   └── filter/
└── configuration/
```

### 6.3 HTTP endpoints

| Method/path | Access | Input | Success |
|---|---|---|---|
| `GET /.well-known/jwks.json` | Public | None | Public RSA JWK set |
| `POST /api/v1/auth/register` | Public | email, optional username, password | 201 account response |
| `POST /api/v1/auth/login` | Public | login, password, optional device name | 200 access token plus refresh cookie |
| `POST /api/v1/auth/refresh` | Public transport; trusted-origin cookie boundary | `refresh_token` cookie | 200 replacement access token plus rotated cookie |
| `POST /api/v1/auth/logout` | Public transport; trusted-origin cookie boundary | `refresh_token` cookie | 204 and cookie clear |
| `POST /api/v1/auth/logout-all` | Valid bearer JWT | JWT subject | 204 and cookie clear |

### 6.4 Registration

- Email is normalized and uniquely persisted.
- Username is optional, normalized, and unique when present.
- Public registration always creates `ROLE_USER`.
- Caller-supplied `role`, `roles`, `authority`, or `authorities` fields are detected and rejected.
- Password is encoded through the application port and Argon2 adapter.
- Account status values: `ACTIVE`, `LOCKED`, `DISABLED`.
- Account roles: `ROLE_USER`, `ROLE_ADMIN`.
- `ROLE_ADMIN` produces `ROLE_ADMIN`, `CATALOG_ADMIN`, and `INVENTORY_ADMIN` authorities in JWTs.

### 6.5 Login

- Accepts normalized email or username login identity.
- Redis throttle is checked before authentication and before successful commit.
- Unknown-account login performs a dummy Argon2 verification to reduce timing difference.
- Wrong password records a throttled failure.
- Successful login stores account login time, session, and current refresh-token digest.
- Optional device name is bounded to 150 characters.
- User-Agent is bounded to the schema length.
- Direct peer IP is stored without trusting forwarded IP headers.
- Raw password, access token, and refresh token are not persisted.

### 6.6 Password policy implementation

Default Argon2 configuration:

| Property | Default/minimum |
|---|---:|
| Salt length | 16 / minimum 16 |
| Hash length | 32 / minimum 32 |
| Parallelism | 1 / minimum 1 |
| Memory | 19,456 KiB / minimum 19,456 |
| Iterations | 2 / minimum 2 |

Properties are bound through validated `AuthenticationProperties` and injected into `Argon2PasswordProofAdapter`.

### 6.7 Access token contract

| Field | Current value/behavior |
|---|---|
| Signature | RS256 |
| JOSE type | `at+jwt` |
| Key ID | Configured `JWT_KEY_ID` |
| Issuer | Configured `JWT_ISSUER` |
| Audience | Configured `JWT_AUDIENCE`, default `flash-sale-api` |
| Subject | User UUID |
| JWT ID | Random UUID |
| Authorities | Derived from account role |
| Access lifetime | 900 seconds / 15 minutes |
| Public key format | X.509 SubjectPublicKeyInfo PEM |
| Private key format | PKCS#8 RSA PEM |
| Minimum private RSA size | 2048 bits |
| Key consistency | Public/private modulus checked at startup |

Key material may come from PEM properties for focused tests or mounted file locations for runtime. JWKS exposes only public fields.

### 6.8 Refresh/session lifecycle

- Refresh value: 32 cryptographically random bytes, URL-safe Base64 without padding.
- Stored representation: SHA-256 lowercase hexadecimal digest.
- Refresh lifetime: 7 days.
- Session lifetime: 30 days.
- One unused, non-revoked current refresh row is enforced per session.
- Rotation retires the current refresh row before inserting its successor.
- Parent/successor links preserve the rotation chain.
- Concurrent/replayed refresh reuse marks the session `COMPROMISED` and revokes its chain.
- Compromise persistence returns a committed outcome before application error translation.
- JWT signing failure after rotation clears the refresh cookie and maps to 503.
- Current logout revokes one session and its token chain.
- Logout-all revokes all active sessions owned by the validated JWT subject.

### 6.9 Cookie and CSRF-style origin boundary

Cookie defaults:

| Attribute | Default |
|---|---|
| Name | `refresh_token` |
| HttpOnly | Enabled |
| Secure | `false` locally; configurable |
| SameSite | `Lax` |
| Path | `/api/v1/auth` |

Trusted-origin filter:

- Applies only to refresh and current-session logout.
- Accepts an exact configured `Origin`.
- Falls back to the origin portion of `Referer`.
- Rejects missing, malformed, blank, or untrusted source with 403.
- Does not run on logout-all because that operation is bearer-protected and not cookie-owned.

### 6.10 Login throttle

Default policy:

| Property | Value |
|---|---:|
| Failure window | 15 minutes |
| Cooldown | 15 minutes |
| Maximum failures | 5 |
| Contention retries | 3 |

Implementation:

- HMAC-derived Redis keys; raw login identity is not used as the key.
- Redis sorted set stores the rolling failure window.
- Redis cooldown key stores the lock interval.
- `WATCH`/`MULTI`/`EXEC` protects concurrent updates.
- Redis failure maps to authentication unavailable instead of silently bypassing login protection.
- Auth-owned 429 response includes `Retry-After`.

### 6.11 Authentication persistence

Liquibase changesets:

```text
001-create-authentication-schema.sql
002-align-refresh-token-hash-type.sql
```

Tables:

| Table | Purpose |
|---|---|
| `users` | Normalized account identity, password hash, role, status, login/lock timestamps |
| `user_sessions` | Per-device session status, metadata, expiry, revocation/compromise |
| `refresh_tokens` | Hashed refresh rotation chain and use/revocation state |

Key database rules:

- Unique normalized email.
- Unique normalized username when present.
- Role check: `ROLE_USER`, `ROLE_ADMIN`.
- Account status check: `ACTIVE`, `LOCKED`, `DISABLED`.
- Session status check: `ACTIVE`, `REVOKED`, `COMPROMISED`.
- Refresh digest must match 64-character lowercase hexadecimal.
- Unique refresh digest.
- One current refresh token per session through a partial unique index.
- Session and refresh expiry constraints.
- Parent/successor self-reference checks.

### 6.12 Cleanup and observability

- `@EnableScheduling` configuration is present.
- Daily cleanup default: `03:00`.
- Inactive/expired state retention default: 30 days.
- Cleanup has run/failure metrics.
- Authentication HTTP operations have bounded operation/outcome metrics.
- Login throttle has failure/unavailable metrics.
- `AuthenticationTraceFilter` manages `X-Trace-Id` request correlation.
- Actuator health/liveness/readiness/info/Prometheus endpoints are configured.
- No Micrometer Tracing bridge, OpenTelemetry exporter, or OTLP configuration exists.

### 6.13 Authentication response/error contracts

Successful Auth body:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-08-01T00:00:00Z"
}
```

Auth failure body:

```json
{
  "success": false,
  "errorCode": "AUTH_ERROR_CODE",
  "message": "Client-safe message",
  "errors": null,
  "timestamp": "2026-08-01T00:00:00Z"
}
```

Auth correlation is returned through the `X-Trace-Id` response header, never in JSON.

Implemented error codes:

```text
AUTH_VALIDATION_FAILED
AUTH_ACCOUNT_ALREADY_EXISTS
AUTH_INVALID_CREDENTIALS
AUTH_TOO_MANY_ATTEMPTS
AUTH_CROSS_SITE_REQUEST_REJECTED
AUTH_REFRESH_TOKEN_INVALID
AUTH_REFRESH_REUSE_DETECTED
AUTH_METHOD_NOT_ALLOWED
AUTH_UNSUPPORTED_MEDIA_TYPE
AUTH_INTERNAL_ERROR
AUTHENTICATION_UNAVAILABLE
```

### 6.14 Authentication verification

```text
.\mvnw.cmd -pl services/authentication-service verify
Tests: 29
Failures: 0
Errors: 0
Result: BUILD SUCCESS
```

Covered groups include domain account/session rules, registration, login timing path, Argon2 properties, Redis throttle, refresh behavior, persistence adapter behavior, trusted origins, JWKS, JWT compatibility, status mapping, observability, and migration startup.

Remaining verification gaps:

- Default verify has no real PostgreSQL refresh-rotation concurrency test.
- Default verify has no real PostgreSQL logout transaction/rollback suite.
- No complete Gateway/Product real-token end-to-end environment smoke test was run in this audit.
- Authentication's local JWT decoder validates issuer/type/signature/time but does not add its own audience validator.
- Feature 015 task ledger is not synchronized with the amount of implemented source.

## 7. Product Service

### 7.1 Module status

| Item | State |
|---|---|
| Public catalog | Implemented and verified |
| Catalog administration | Implemented core create/read/composition/lifecycle slice |
| Source files | 121 production Java files |
| Test classes | 10 |
| Web runtime | Spring MVC |
| Database | PostgreSQL `product_db` |
| Mapping | MapStruct at public/admin web boundaries |
| Redis | Not used |
| Kafka/outbox | Deferred; no Product outbox table or publisher |
| OpenAPI/Swagger | Springdoc 2.8.17, disabled by default; see [unified API catalog](api/README.md) |

### 7.2 Feature-oriented package structure

```text
com.philia.flashsale.product/
├── ProductServiceApplication.java
├── catalog/
│   ├── domain/
│   ├── application/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   └── service/
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
├── catalogadmin/
│   ├── domain/
│   │   ├── exception/
│   │   └── policy/
│   ├── application/
│   │   ├── command/
│   │   ├── exception/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   ├── query/
│   │   ├── result/
│   │   ├── service/
│   │   └── usecase/
│   └── adapter/
│       ├── in/web/
│       └── out/persistence/
└── configuration/
```

### 7.3 Public catalog endpoints

| Method/path | Query/path input | Response |
|---|---|---|
| `GET /api/v1/catalog/categories` | Optional `parentId` UUID | `CatalogListResponse<CategoryResponse>` |
| `GET /api/v1/catalog/products` | Optional `categorySlug`; page default 0; size default 20, max 100 | Shared `PageResponse<ProductSummaryResponse>` |
| `GET /api/v1/catalog/products/{slug}` | Product slug | `ProductDetailResponse` |

Public visibility rule:

- Product status must be `ACTIVE`.
- `published_at` must exist and be no later than current database time.
- At least one `ACTIVE` variant must exist.
- Public responses include only active variants.
- Public media query includes only active media.
- Public category browsing includes only active categories.

Public Product summary fields:

```text
id, code, slug, name, shortDescription, variants
```

Public Product detail fields:

```text
id, code, slug, name, shortDescription, description,
variants, categories, media
```

Variant price presentation:

```text
variant.id, sku, name, basePrice, currency
```

No Product-level minimum/maximum price aggregation is implemented.

### 7.4 Catalog administration endpoints

| Method/path | Required headers | Input/output |
|---|---|---|
| `POST /api/v1/admin/catalog/products` | `Authorization`, `Idempotency-Key`, `X-Trace-Id` | Create Product draft; 201 plus Location |
| `GET /api/v1/admin/catalog/products` | `Authorization`, `X-Trace-Id` | Optional status/search; paginated summaries |
| `GET /api/v1/admin/catalog/products/{productId}` | `Authorization`, `X-Trace-Id` | Full admin Product detail |
| `PUT /api/v1/admin/catalog/products/{productId}/composition` | `Authorization`, `If-Match`, `X-Trace-Id` | Atomically replace editable Product/Variant/Category/Media composition |
| `POST /api/v1/admin/catalog/products/{productId}/publish` | `Authorization`, `If-Match`, `Idempotency-Key`, `X-Trace-Id` | Move Draft/Inactive Product to Active |
| `POST /api/v1/admin/catalog/products/{productId}/deactivate` | Same lifecycle headers | Move Active Product to Inactive |
| `POST /api/v1/admin/catalog/products/{productId}/archive` | Same lifecycle headers | Move non-archived Product to final Archived |

There is no dedicated `/reactivate` HTTP endpoint. The lifecycle application service implements `ReactivateProductUseCase`, while the current `/publish` path can transition `INACTIVE -> ACTIVE`.

### 7.5 Product domain and administration rules

Product statuses:

```text
DRAFT, ACTIVE, INACTIVE, ARCHIVED
```

Variant statuses:

```text
ACTIVE, INACTIVE, ARCHIVED
```

Allowed Product transitions:

```text
DRAFT -> ACTIVE
ACTIVE -> INACTIVE
INACTIVE -> ACTIVE
any non-ARCHIVED -> ARCHIVED
```

Rules implemented:

- Archived Product is terminal and immutable.
- Publication requires at least one active Variant with a valid non-negative VND price.
- Product code, slug, and name are required.
- Product code and slug are unique.
- Variant SKU is unique.
- Non-null Variant barcode is unique.
- Published active Product Variant SKU cannot be changed.
- Variant belongs to its Product before mutation.
- Variant money must be non-negative VND with at most four decimal places.
- Variant/category/media sort order cannot be negative.
- At most one primary Category membership.
- Every requested Category must already exist.
- Variant-bound media must reference a Variant owned by the Product.
- Media type is `IMAGE` or `VIDEO`.
- Media status is `ACTIVE`, `INACTIVE`, or `ARCHIVED`.
- `If-Match` version mismatch produces a stale-version conflict.
- Composition is persisted atomically as one command.

### 7.6 Product idempotency and audit

- Create-draft and lifecycle commands require idempotency keys.
- Idempotency ownership is scoped by actor plus key.
- Same actor/key/request hash within 7 days replays the stored outcome.
- Same key with different request hash returns conflict.
- At/after 7-day expiry the key may be reclaimed for a fresh command.
- Historical audit rows are retained after idempotency replay expiry.
- Audit stores actor, trace, command, target Product, outcome, error code, Product version, and timestamp.
- Audit outcomes: `SUCCESS`, `REPLAYED`, `REJECTED`, `CONFLICT`.

### 7.7 Product persistence schema

Liquibase changesets:

```text
001-create-product-catalog-schema.sql
002-create-product-admin-support.sql
```

Tables:

| Table | Purpose |
|---|---|
| `categories` | Hierarchical catalog categories |
| `products` | Product aggregate root and publication lifecycle |
| `product_variants` | Sellable SKU, barcode, price, currency, weight, status |
| `product_categories` | Ordered Product/Category membership and primary flag |
| `product_media` | Product/Variant image or video metadata |
| `product_admin_idempotency_keys` | Seven-day replay outcomes |
| `product_admin_audit_logs` | Durable administration audit trail |

Important database integrity:

- UUID primary keys.
- Unique Product/Category code and slug.
- Unique Variant SKU and non-null barcode.
- VND-only Variant currency check.
- Non-negative base price.
- JSONB Product/Variant attributes must be objects.
- Product/Category/Variant/Media status constraints.
- Partial unique index for one primary Category per Product.
- Composite Product/Variant media ownership foreign key.
- Positive optional media dimensions/file size and Variant weight.
- Optimistic `version` columns on Product, Category, and Variant storage.
- Idempotency actor/key unique constraint.
- Admin audit indexes by Product, actor, and trace.

### 7.8 Product security

- Public catalog is permitted without authentication.
- Admin catalog requires `CATALOG_ADMIN` in Gateway and Product service.
- Product service revalidates the JWT locally from Authentication JWKS.
- Product JWT decoder validates signature, issuer, audience, non-blank subject, time, and `typ=at+jwt`.
- Product derives actor identity from the authenticated JWT subject/name.
- Caller-provided actor headers are not trusted.
- Admin 401/403 behavior is handled by `ProductAdminSecurityFailureHandler`.

### 7.9 Product HTTP response formats

Current success behavior:

- Public list/detail endpoints wrap Product-owned DTOs in shared `ApiResponse<T>`.
- Public/admin pagination uses shared `PageResponse<T>`.
- Admin create/composition/lifecycle endpoints wrap Product-owned DTOs in shared `ApiResponse<T>`.

Public catalog error:

```json
{
  "success": false,
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "Product was not found"
}
```

Admin catalog error:

```json
{
  "success": false,
  "errorCode": "STALE_PRODUCT_VERSION",
  "message": "Client-safe detail",
  "errors": null,
  "timestamp": "2026-08-01T00:00:00Z"
}
```

All Product responses carry correlation in the `X-Trace-Id` header only; JSON does not contain
`traceId`.

Admin error groups include Product/Category not found, duplicate Product identifiers, duplicate Variant SKU/barcode, reused idempotency key, stale version, domain lifecycle failures, malformed input, and missing headers.

### 7.10 Product verification

```text
.\mvnw.cmd -pl services/product-service -am verify
Tests: 32
Failures: 0
Errors: 0
Result: BUILD SUCCESS
Database: PostgreSQL 17 Testcontainers
Liquibase changesets applied in test: 2
```

Covered groups:

- Public Product visibility and query behavior.
- Product aggregate draft rules.
- Lifecycle policy.
- Create-draft use case and idempotency.
- Admin HTTP create/composition/lifecycle behavior.
- Admin persistence integration.
- Product JWT trust validation.
- Liquibase migration startup.

Remaining Product gaps:

- No admin Category CRUD lifecycle.
- No dedicated reactivate HTTP route.
- No Product Kafka events or outbox.
- No public cloud OpenAPI exposure; local service documents are generated by Springdoc when opted in.
- No Redis cache.
- Feature 010 status wording still contains older “US2/US3 pending” text although current code/tasks include composition and lifecycle implementation.

## 8. Inventory Service

### 8.1 Module status

| Item | State |
|---|---|
| Physical stock | Implemented core |
| Stock movement ledger | Implemented core |
| Campaign allocations | Implemented core |
| Outbox persistence | Implemented |
| Kafka publication/consumption | Deferred |
| Source files | 72 production Java files |
| Test classes | 7 |
| Web runtime | Spring MVC |
| Database | PostgreSQL `inventory_db` |
| OpenAPI | Springdoc 2.8.17, disabled by default; supports the shared local opt-in |

### 8.2 Feature-local Clean/Hexagonal structure

```text
com.philia.flashsale.inventory/
├── InventoryServiceApplication.java
├── stock/
│   ├── domain/
│   │   ├── model/
│   │   └── exception/
│   ├── application/
│   │   ├── command/
│   │   ├── query/
│   │   ├── result/
│   │   ├── exception/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   └── usecase/
│   └── adapter/
│       ├── in/web/{request,response,mapper}/
│       └── out/persistence/jpa/{entity,repository,mapper}/
├── allocation/
│   ├── domain/{model,exception}/
│   ├── application/{command,result,exception,port,usecase}/
│   └── adapter/
│       ├── in/web/{request,response,mapper}/
│       └── out/persistence/jpa/{entity,repository,mapper}/
├── movement/
│   ├── domain/model/
│   ├── application/{query,result,port,usecase}/
│   └── adapter/
│       ├── in/web/{response,mapper}/
│       └── out/persistence/jpa/{entity,repository,mapper}/
├── outbox/
│   └── adapter/out/persistence/jpa/{entity,repository}/
├── configuration/
└── websupport/error/
```

Architecture enforcement test checks that application packages do not depend on adapters or Spring Data paging types.

### 8.3 Inventory HTTP endpoints

Admin endpoints through Gateway:

| Method/path | Required authority | Input/output |
|---|---|---|
| `GET /api/v1/admin/inventory/{variantId}` | `INVENTORY_ADMIN` | Current physical/allocated/available stock |
| `POST /api/v1/admin/inventory/{variantId}/adjustments` | `INVENTORY_ADMIN` | Increase/decrease physical stock |
| `GET /api/v1/admin/inventory/{variantId}/movements` | `INVENTORY_ADMIN` | Immutable movement page |

Internal endpoints not routed by Gateway:

| Method/path | Required authority | Input/output |
|---|---|---|
| `POST /internal/v1/campaign-stock-allocations` | `SCOPE_INVENTORY_WRITE` | Allocate stock to a campaign |
| `POST /internal/v1/campaign-stock-allocations/{requestId}/release` | `SCOPE_INVENTORY_WRITE` | Release active allocation |
| `POST /internal/v1/campaign-stock-allocations/{requestId}/reconcile` | `SCOPE_INVENTORY_WRITE` | Set sold/returned terminal outcome |

Initialization exists as an application use case but has no HTTP, Kafka, or other inbound adapter.

### 8.4 Stock capability

Inventory state:

```text
variantId
skuSnapshot
onHandQuantity
campaignAllocatedQuantity
availableQuantity = onHandQuantity - campaignAllocatedQuantity
version
timestamps
```

Rules:

- One Inventory item per Product Variant.
- Physical and allocated quantities are non-negative.
- Allocated quantity cannot exceed on-hand quantity.
- Increase/decrease quantities must be positive.
- Physical decrease cannot reduce on-hand below allocated stock.
- Allocation cannot exceed available stock.
- Release cannot make allocated stock negative.
- Reconciliation removes sold quantity from on-hand and the entire allocation from allocated stock.
- Inventory row is loaded with a pessimistic write lock for mutation.
- Admin adjustment request ID is idempotent.
- Reusing an adjustment request ID for another Inventory item is rejected.
- Every state mutation records a movement in the same application transaction.

Adjustment request:

```text
requestId, type(INCREASE|DECREASE), positive quantity, reason(max 500)
```

### 8.5 Movement capability

Movement fields:

```text
id, requestId, inventoryItemId, allocationId,
referenceType, referenceId, movementType,
onHandDelta, allocatedDelta,
onHandAfter, allocatedAfter,
reason, createdAt
```

Implemented movement types include physical stock in/adjustment and campaign allocate/release/reconcile transitions.

Pagination:

- Default page: 0.
- Default size: 20.
- Maximum size: 100.
- Order: `createdAt DESC, id DESC`.
- Application page model is framework-neutral.
- Web adapter maps to shared `PageResponse<T>`/`PageMeta`.

### 8.6 Campaign allocation capability

Allocation state:

```text
id, requestId, campaignId, inventoryItemId, variantId,
allocatedQuantity, soldQuantity, returnedQuantity,
status, createdAt, updatedAt, reconciledAt
```

Statuses:

```text
ACTIVE, RELEASED, RECONCILED
```

Rules:

- Allocation quantity must be positive.
- One request ID identifies one idempotent allocation command.
- One campaign/variant allocation is enforced by database uniqueness.
- Same request ID with different campaign, variant, or quantity is rejected.
- Release and reconcile operate only from `ACTIVE`.
- Repeated release of an already released allocation returns the stored result.
- Repeated reconcile of an already reconciled allocation returns the stored result.
- Reconciliation requires non-negative sold/returned values.
- `sold + returned` must equal allocated quantity.
- Inventory row is pessimistically locked before allocation/release/reconcile.
- Allocation, Inventory update, movement, and outbox record occur inside one local transaction.

### 8.7 Inventory persistence schema

Liquibase changeset:

```text
001-create-inventory-schema.sql
```

Tables:

| Table | Purpose |
|---|---|
| `inventory_items` | Per-Variant physical and campaign-allocated balance |
| `campaign_stock_allocations` | Campaign allocation lifecycle and terminal settlement |
| `stock_movements` | Immutable balance-change audit ledger |
| `outbox_events` | Durable pending allocation events |

Key constraints:

- Unique `inventory_items.variant_id`.
- Non-negative balances and allocated <= on-hand.
- Unique allocation `request_id`.
- Unique allocation `(campaign_id, variant_id)`.
- Allocation quantity positive.
- Sold/returned non-negative and not greater than allocation.
- Unique movement `request_id`.
- Movement must change on-hand or allocated balance.
- Outbox status: `PENDING`, `PUBLISHED`, `FAILED`.
- Outbox retry count non-negative.
- Pending outbox partial index.
- Movement order index by Inventory item, timestamp descending, ID descending.

### 8.8 Inventory outbox state

Currently persisted allocation event types:

```text
CampaignStockAllocated
CampaignStockReleased
CampaignStockReconciled
```

Current state:

- JSON payload string is persisted in `outbox_events` in the allocation transaction.
- Aggregate type is `CAMPAIGN_STOCK_ALLOCATION`.
- No polling/claim scheduler exists.
- No Kafka publisher exists.
- No retry/backoff/dead-letter policy is implemented.
- No versioned promoted event schema exists.

### 8.9 Inventory security and API documentation

Security:

- Admin endpoints require `INVENTORY_ADMIN`.
- Internal allocation endpoints require `SCOPE_INVENTORY_WRITE`.
- Actuator health/info/Prometheus paths are public.
- Swagger/OpenAPI paths are permitted when enabled.
- Authorities are read from JWT `authorities`, `roles`, and space-separated `scope`.
- JWT signature/issuer/time use Spring Resource Server configuration.
- Inventory does not currently add explicit audience or `typ=at+jwt` validators like Gateway/Product.

OpenAPI:

- Dependency: `springdoc-openapi-starter-webmvc-ui` 2.8.17.
- Disabled by default with `INVENTORY_API_DOCS_ENABLED=false`.
- Swagger UI: `/swagger-ui.html`.
- OpenAPI JSON: `/v3/api-docs`.
- OpenAPI YAML: `/v3/api-docs.yaml`.
- Documentation annotations are separated into `InventoryAdminApi`, `InventoryMovementApi`, and `InventoryInternalApi` interfaces.

### 8.10 Inventory HTTP envelopes

Success uses shared `ApiResponse<T>`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "UTC timestamp"
}
```

Failures use shared `ApiErrorResponse`:

```json
{
  "success": false,
  "errorCode": "INVENTORY_OPERATION_REJECTED",
  "message": "Client-safe detail",
  "errors": null,
  "timestamp": "UTC timestamp"
}
```

Current mapped error behavior:

| Case | Status/code |
|---|---|
| Invalid request/argument | 400 `VALIDATION_ERROR` |
| Missing Inventory/allocation inferred from message | 404 `INVENTORY_NOT_FOUND` |
| Domain/application rejection | 409 `INVENTORY_OPERATION_REJECTED` |
| Authentication/authorization | Spring Security 401/403 response |

### 8.11 Inventory verification

```text
.\mvnw.cmd -pl services/inventory-service -am verify
Tests: 10
Failures: 0
Errors: 0
Result: BUILD SUCCESS
Database: PostgreSQL 17 Testcontainers
Liquibase changesets applied in test: 1
```

Covered groups:

- Inventory quantity invariants.
- Stock application adjustment behavior.
- Allocation domain lifecycle.
- Allocation application orchestration.
- Movement paging orchestration.
- Clean Architecture dependency checks.
- Application context, PostgreSQL schema, Liquibase, and route registration.

Remaining Inventory gaps:

- No initialization inbound adapter.
- No Kafka integration.
- No outbox publisher/retry/recovery.
- No PostgreSQL concurrency suite for allocation and idempotency.
- No focused web contract/security tests for all six endpoints.
- No complete Compose smoke run for migration, JWT, and concurrency in this audit.
- No explicit JWT audience/type validation.
- Error not-found classification currently depends on exception message text.
- No trace filter or Micrometer distributed tracing.
- Feature 016 task ledger remains mostly unchecked despite implemented core source.

## 9. Scaffold-only business services

The following six modules share this implemented baseline:

- One `@SpringBootApplication` bootstrap class.
- One `@SpringBootTest` context-load test.
- Spring MVC dependency.
- Spring Boot Actuator dependency.
- Runtime Micrometer Prometheus registry.
- Liquibase dependency.
- Empty `db.changelog-master.yaml`.
- Declarative health/liveness/readiness/info/Prometheus exposure.
- Service-local multi-stage Dockerfile.
- Non-root Java 21 JRE runtime image.
- Compose service entry and local debug port.
- No controller, domain, application service, repository, JPA entity, database driver, broker adapter, cache adapter, JWT security, or business migration.

### 9.1 Cart Service

| Item | Current state |
|---|---|
| Bootstrap | `CartServiceApplication` |
| Test | `CartServiceApplicationTests` |
| Logical local DB | `cart_db` created by PostgreSQL init |
| Debug port | 18089 |
| Business API/schema | Deferred |
| Boundary ADR | ADR 0002 accepted |

### 9.2 Campaign Service

| Item | Current state |
|---|---|
| Bootstrap | `CampaignServiceApplication` |
| Test | `CampaignServiceApplicationTests` |
| Logical local DB | `campaign_db` |
| Debug port | 18083 |
| Campaign administration/snapshots/scheduling | Not implemented |
| Inventory internal client | Not implemented |
| Kafka events | Not implemented |

### 9.3 Flash Sale Service

| Item | Current state |
|---|---|
| Bootstrap | `FlashsaleServiceApplication` |
| Test | `FlashsaleServiceApplicationTests` |
| Logical local DB | `flashsale_db` |
| Debug port | 18084 |
| Redis Lua reservation | Not implemented |
| Distributed lock/reservation recovery | Not implemented |
| Purchase acceptance API | Not implemented |
| Kafka order request | Not implemented |

### 9.4 Order Service

| Item | Current state |
|---|---|
| Bootstrap | `OrderServiceApplication` |
| Test | `OrderServiceApplicationTests` |
| Logical local DB | `order_db` |
| Debug port | 18085 |
| Order aggregate/API | Not implemented |
| Idempotency/outbox | Not implemented |
| Kafka workflow | Not implemented |

### 9.5 Payment Service

| Item | Current state |
|---|---|
| Bootstrap | `PaymentServiceApplication` |
| Test | `PaymentServiceApplicationTests` |
| Logical local DB | `payment_db` |
| Debug port | 18086 |
| Payment transaction/provider adapter | Not implemented |
| Provider idempotency/reconciliation | Not implemented |
| Kafka workflow | Not implemented |

### 9.6 Notification Service

| Item | Current state |
|---|---|
| Bootstrap | `NotificationServiceApplication` |
| Test | `NotificationServiceApplicationTests` |
| Logical local DB | `notification_db` |
| Debug port | 18087 |
| Email/SMS/push providers | Not implemented |
| Notification preference/history | Not implemented |
| Kafka consumer | Not implemented |

### 9.7 Scaffold verification

```text
.\mvnw.cmd -pl services/cart-service,services/campaign-service,services/flashsale-service,services/order-service,services/payment-service,services/notification-service verify
Tests: 6 total, one per service
Failures: 0
Errors: 0
Result: BUILD SUCCESS
```

## 10. Database ownership map

| Service | Logical local database | Business schema state |
|---|---|---|
| API Gateway | None | No database |
| Authentication | `auth_db` | 3 tables, 2 changesets |
| Product | `product_db` | 7 tables, 2 changesets |
| Inventory | `inventory_db` | 4 tables, 1 changeset |
| Cart | `cart_db` | Empty changelog |
| Campaign | `campaign_db` | Empty changelog |
| Flash Sale | `flashsale_db` | Empty changelog |
| Order | `order_db` | Empty changelog |
| Payment | `payment_db` | Empty changelog |
| Notification | `notification_db` | Empty changelog |

Local development uses one PostgreSQL 17 container with separate logical databases. Business table ownership remains service-local.

## 11. Local Docker implementation

### 11.1 Shared platform

`infra/docker/compose.yml` currently defines:

| Component | Image/default | Host exposure |
|---|---|---|
| PostgreSQL | `postgres:17-alpine` | Loopback `5432` |
| Redis | `redis:7.4-alpine` | Loopback `6379`, password required, AOF enabled |
| Kafka | `apache/kafka:4.0.0` | Loopback `29092`, single-node KRaft |
| Confluent Schema Registry | `confluentinc/cp-schema-registry:8.3.0` | Loopback `8081`, Kafka-backed `_schemas` topic |
| API Gateway | Service image | Loopback `8080` |
| 9 business services | Service images under `apps` profile | Internal only by default |

### 11.2 Compose files

| File | Purpose |
|---|---|
| `infra/docker/compose.yml` | Platform plus optional `apps` profile |
| `infra/docker/compose.dev.yml` | Direct service debug ports |
| `infra/docker/.env.example` | Committed placeholders only |
| `infra/docker/postgres/init/01-create-databases.sql` | Creates logical local databases |

### 11.3 Container behavior

- Service builds use repository-root context.
- Maven build stage packages only the selected service plus reactor dependencies.
- Runtime uses Eclipse Temurin Java 21 JRE.
- Runtime user/group is `spring`; containers do not run as root.
- Default application port is 8080.
- Backend Compose services receive virtual-thread environment configuration.
- Application replicas keep Liquibase disabled by default in Compose.
- Authentication mounts an RSA key directory read-only.
- PostgreSQL, Redis, and Kafka bind to `127.0.0.1` by default.
- API Gateway is the only default host-exposed application.

### 11.4 Required local secrets/configuration

```text
POSTGRES_PASSWORD
REDIS_PASSWORD
RATE_LIMIT_KEY_HMAC_SECRET             when Gateway limiter is enabled
AUTH_THROTTLE_HMAC_SECRET
AUTH_JWT_KEY_DIR
JWT issuer/audience/key ID
AUTH_TRUSTED_ORIGINS
```

Committed `.env.example` contains replacement markers, not deployable secrets.

### 11.5 Current Compose gaps

- A local full-topology smoke run passed for Gateway, Authentication, Product, Inventory,
  PostgreSQL, and Redis; it is not yet automated in CI.
- Scaffold services depend on the shared platform in Compose but contain no platform adapters.
- No HTTPS termination is implemented in the repository; VPS guidance assumes an external reverse proxy/load balancer.
- No production Compose file is intended; Kubernetes remains the target production topology.

## 12. Kubernetes, Helm, and monitoring

### 12.1 Kubernetes

Current state:

- `infra/k8s/README.md` only.
- No `Deployment`, `Service`, `Ingress`, Gateway API, ConfigMap, Secret, Job, Kustomize base, or overlay.
- No `kubectl apply --dry-run` target exists yet.

Documented target:

```text
infra/k8s/base/<service>/
infra/k8s/overlays/dev/
infra/k8s/overlays/staging/
infra/k8s/overlays/prod/
```

### 12.2 Helm

- `infra/helm/README.md` only.
- No chart, values, templates, or release ownership is implemented.

### 12.3 Monitoring/telemetry stack

- `infra/monitoring/README.md` only.
- No Prometheus scrape configuration.
- No Grafana provisioning/dashboard.
- No alert rules.
- No OpenTelemetry Collector.
- No Tempo.
- No Loki.
- No Grafana Alloy.

Application-side current state:

- Every service has Actuator plus runtime `micrometer-registry-prometheus`.
- No manual Prometheus registry construction.
- Authentication and Gateway have service-local bounded metrics/log observations.
- No service has Micrometer Tracing bridge to OpenTelemetry.
- No OTLP exporter or W3C propagation test is installed.

## 13. Communication and event state

### 13.1 Implemented synchronous communication

```text
Client
  -> API Gateway WebFlux
     -> Authentication Service HTTP
     -> Product Service HTTP
     -> Inventory Service admin HTTP
```

Implemented behavior:

- Gateway route forwarding.
- Authorization header pass-through.
- Query/body/header/response pass-through tests for Product admin.
- JWT verification locally at Gateway and Product.
- JWT verification at Inventory through Spring Resource Server.

Not implemented:

- Service-owned outbound HTTP business clients (`RestClient`, OpenFeign, HTTP Interface, or
  `WebClient`) are not implemented yet.
- Circuit breaker/fallback implementation.
- Bounded retry policies for business calls.
- Service workload-identity token issuance.
- gRPC/protobuf.
- WebSocket.

### 13.2 Kafka

Infrastructure state:

- Kafka 4.0.0 single-node local broker configured in Compose.
- Confluent Schema Registry 8.3.0 is available for local schema validation, but no service adopts
  a registry serializer or deserializer yet.
- Empty repository-level `contracts/asyncapi` and `contracts/events` directories.

Application state:

- No Spring Kafka dependency in any service.
- No `KafkaTemplate`.
- No `@KafkaListener`.
- No producer/consumer configuration.
- No topic creation.
- No consumer idempotency table.
- No dead-letter/retry/replay implementation.
- Inventory persists outbox rows but does not publish them.
- Product Kafka/outbox integration is explicitly deferred.

### 13.3 Redis

| Owner | Implemented use |
|---|---|
| API Gateway | Distributed token-bucket rate limiter |
| Authentication | Login failure window and cooldown throttle |
| Product | None |
| Inventory | None |
| Flash Sale | Planned only; no Lua reservation implementation |

## 14. HTTP response standardization state

| Boundary | Success shape | Error shape |
|---|---|---|
| Gateway-owned failure | N/A | Shared `ApiErrorResponse` (`errorCode`, safe message, optional errors) |
| Authentication | Shared `ApiResponse<T>` or 204 | Shared `ApiErrorResponse` |
| Product public | Shared `ApiResponse<T>` with shared `PageResponse<T>` where paged | Shared `ApiErrorResponse` |
| Product admin | Shared `ApiResponse<T>` with shared `PageResponse<T>` where paged | Shared `ApiErrorResponse` |
| Inventory | Shared `ApiResponse<T>` | Shared `ApiErrorResponse` |
| Scaffold services | No business contract | Default Spring behavior only |

All migrated HTTP boundaries use `libs/common-web`; `X-Trace-Id` is header-only and is never emitted
inside JSON. Gateway downstream pass-through intentionally does not rewrite service-owned bodies.

## 15. Spec Kit feature inventory

Task counts below are ledger counts, not independent proof of implementation.

| Feature | Artifact status | Checked | Open | Current result |
|---|---|---:|---:|---|
| 001 Maven service scaffold | Approved | 58 | 0 | Reactor/service skeleton created |
| 002 Clean/Hex scaffold | Approved | 21 | 0 | Initial architecture baseline created; later refactored by feature |
| 003 Technology/architecture docs | Approved | 12 | 0 | Documentation set created |
| 004 Liquibase setup | Approved | 33 | 0 | Master changelogs/dependencies configured |
| 005 Container/Compose/K8s readiness | Draft | 27 | 0 | Docker/Compose implemented; K8s remains planned |
| 006 Clean Architecture baseline | Approved | 21 | 0 | Working package baseline documented |
| 007 Cart scaffold | Approved | 28 | 0 | Cart module exists; behavior deferred |
| 008 Product schema | Approved | 27 | 0 | Product schema implemented |
| 009 Product catalog query | Verified | 20 | 0 | Public catalog implemented |
| 010 Catalog administration | Implementing wording | 71 | 1 | Create/read/composition/lifecycle code implemented; root verify open |
| 011 Gateway errors | Verified | 25 | 0 | Gateway-owned failure pipeline implemented |
| 012 Gateway 429 contract | Verified | 9 | 0 | 429 body/header contract implemented |
| 013 Redis rate limiter | Approved | 21 | 30 | Core token bucket plus simplified identity/HMAC/wiring implemented; full US3 remains |
| 014 JWT trust foundation | Verified | 13 | 0 | Auth issuer/JWKS and Gateway/Product validation implemented |
| 015 Authentication session MVP | Approved | 10 | 62 | Large implementation exists; ledger is substantially unsynchronized |
| 016 Inventory | Core HTTP/PostgreSQL approved; Kafka deferred | 2 | 39 | Core implementation exists; ledger is substantially unsynchronized |

## 16. Accepted ADR inventory

| ADR | Decision |
|---|---|
| 0001 | Root infrastructure ownership |
| 0002 | Cart service boundary |
| 0003 | Lean API Gateway package structure |
| 0004 | Gateway distributed rate limiter |
| 0005 | Authentication JWT trust foundation |
| 0006 | Authentication session and refresh rotation |
| 0007 | Feature-oriented Authentication packages |
| 0008 | Feature-oriented Product packages |
| 0009 | Shared Product HTTP pagination contract |
| 0010 | Chatting Service renamed to Inventory Service |
| 0011 | Inventory feature-local Hexagonal boundaries |

## 17. Existing documentation inventory

### Architecture

```text
docs/architecture/ddd-clean-hexagonal-quick-reference.md
docs/architecture/service-clean-hex-structure.md
docs/architecture/service-communication-protocols.md
docs/architecture/diagrams/system-overview.md
docs/architecture/diagrams/system-overview.mmd
```

### Service-specific

```text
docs/authen/01-class-responsibilities.md
docs/authen/02-authentication-flows.md
docs/authen/03-boundary-rules.md
docs/authen/04-authentication-service-structure.md
docs/product/README.md
docs/inventory/README.md
```

### Database

```text
docs/database/authentication-service-schema.md
docs/database/product-service-schema.md
docs/database/inventory-service-schema.md
```

### Gateway rate limiting

```text
docs/ratelimit/01-scope-and-status.md
docs/ratelimit/02-http-contract.md
docs/ratelimit/03-policy-and-configuration.md
docs/ratelimit/04-token-bucket-algorithm.md
docs/ratelimit/05-redis-lua-state.md
docs/ratelimit/06-gateway-architecture-and-build.md
docs/ratelimit/07-identity-and-security.md
docs/ratelimit/08-failure-and-recovery.md
docs/ratelimit/09-observability.md
docs/ratelimit/10-testing-and-validation.md
docs/ratelimit/11-runtime-and-kubernetes.md
docs/ratelimit/12-delivery-slices-and-decisions.md
```

### Process/technology/deployment

```text
docs/spec-driven-development/01-feature-spec-structure.md
docs/spec-driven-development/02-spec-kit-artifact-system.md
docs/spec-driven-development/03-coding-constraints.md
docs/technology/technology-problem-map.md
docs/technology/liquibase-migration-rules.md
docs/deployment/container-compose-k8s-strategy.md
```

## 18. Current validation snapshot

| Command | Result | Tests |
|---|---|---:|
| `.\mvnw.cmd clean verify` | Passed | 257 total |
| `.\mvnw.cmd -pl services/api-gateway -am clean verify` | Tests passed before command-runner timeout | 177, 0 failures/errors |
| `.\mvnw.cmd -pl services/authentication-service verify` | Passed | 29 |
| `.\mvnw.cmd -pl services/product-service -am verify` | Passed | 32 |
| `.\mvnw.cmd -pl services/inventory-service -am verify` | Passed | 13 in latest reactor |
| Six scaffold modules in one Maven invocation | Passed | 6 |

The Gateway taxonomy test now covers all nine Gateway-owned codes, including
`INVENTORY_ADMIN_REQUIRED`.

Runtime validation completed on 2026-07-29:

- Four application images built: Gateway, Authentication, Product, and Inventory.
- Authentication, Product, and Inventory one-off Liquibase runs exited `0`.
- Gateway/Auth/Product/Inventory health endpoints returned 200.
- Auth JWKS exposed one RSA/RS256 public signing key with no private fields.
- Registration, login, refresh rotation, Gateway/downstream JWT validation, Product admin,
  Inventory authorization/not-found, logout, and post-logout refresh rejection passed.
- Redis-down behavior passed: Gateway stayed healthy and failed open for Product catalog; Auth failed
  closed with `503 AUTHENTICATION_UNAVAILABLE`; Auth recovered after the Redis client reconnected.
- Detailed evidence: [Local Compose Smoke Validation](local-compose-smoke-validation.md).

Warnings observed during verification:

- Mockito dynamically self-attaches the Byte Buddy agent; future JDKs will require explicit agent configuration.
- Product and Inventory default verification require a working Docker environment for PostgreSQL Testcontainers.

Validation still not executed:

- Authentication `auth-migration-it` Maven profile; the real one-off Auth Liquibase container run passed.
- Product `product-migration-it` Maven profile; the real one-off Product Liquibase container run passed.
- Kubernetes validation; no manifests exist.
- Load tests; no scripts exist.
- Browser UI flow; the backend HTTP/JWT topology was exercised without a frontend.
- Refresh-reuse concurrency and the remaining Inventory concurrency/reconciliation quickstart scenarios.

## 19. Known code/document/artifact drift

1. Root `README.md` still describes the repository as a skeleton without business logic.
2. `docs/architecture/diagrams/system-overview.md` still labels implemented Auth/Product/Inventory capabilities as current shells/planned data.
3. `docs/technology/technology-problem-map.md` still describes Gateway routes and PostgreSQL schemas as planned in several sections.
4. Feature 010 status text still contains older US2/US3-pending wording although composition/lifecycle source and task evidence exist.
5. Feature 015 task ledger has 62 unchecked entries while the corresponding Authentication implementation largely exists under refactored package paths.
6. Feature 016 task ledger has 39 unchecked entries while core PostgreSQL/HTTP implementation exists.
7. Feature 013 task ledger leaves identity/HMAC tasks unchecked even though simplified source/tests exist.
8. `contracts/events/chatting` remains as an empty stale directory after Chatting was renamed to Inventory.
9. Product and Inventory/Auth use different success/error envelope shapes.
10. Inventory Swagger security paths are permitted in security configuration even when documentation generation is disabled by properties.

## 20. Remaining system work by priority area

### Build consistency

- Keep the root `clean verify` green as new features are introduced.
- Synchronize Feature 010, 013, 015, and 016 task ledgers with actual implementation/evidence.

### Authentication completion

- Add real PostgreSQL concurrency and rollback suites for refresh/logout.
- Add audience validation to Authentication's local JWT decoder if governed by the approved trust contract.
- Convert the successful local Gateway/Auth/Product/Inventory smoke flow into repeatable CI automation when CI infrastructure is available.
- Complete deploy-time security/status/observability regression evidence.

### Product completion

- Decide and specify remaining Category administration.
- Decide whether a dedicated reactivate route is required or `/publish` remains canonical.
- Add Kafka/outbox only in a separately approved integration feature.
- Promote/version an OpenAPI contract when required.

### Inventory completion

- Approve and implement initialization transport.
- Approve Kafka topic/version/rejection/outbox retry policies.
- Implement outbox publisher after those decisions.
- Complete the remaining movement-pagination, PostgreSQL concurrency, HTTP contract, allocation,
  reconciliation, and rollback smoke scenarios from Feature 016.
- Replace message-text error classification with typed codes/outcomes.
- Add trace/correlation handling.

### Remaining business services

- Create a separate approved feature/spec/plan/tasks set before adding each service's first domain behavior.
- Add business schema migrations only with the owning service feature.
- Add HTTP/event contracts before clients/producers/consumers.

### Platform

- Implement Kubernetes bases/overlays and per-service migration Jobs.
- Add HTTPS ingress/reverse-proxy deployment assets for VPS/Kubernetes.
- Add Micrometer Tracing, OpenTelemetry bridge/exporter, Collector, and Tempo only through an approved observability feature.
- Add Prometheus server, Grafana dashboards, and alert rules under `infra/monitoring`.
- Add k6 scenarios for Gateway/Auth/Product/Inventory critical paths.
