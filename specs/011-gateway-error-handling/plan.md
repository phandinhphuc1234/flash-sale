# Implementation Plan: Gateway-Owned Error Handling

**Branch**: `011-gateway-error-handling` | **Date**: 2026-07-22 | **Spec**: [spec.md](spec.md)
**Input**: Approved feature specification from `specs/011-gateway-error-handling/spec.md`
**Plan status**: Verified | **Technical owner**: Gateway/Platform owner
**Required reviewers**: Gateway technical owner, security reviewer, client-contract reviewer

> The user approved Options A for the failure taxonomy, trace generation, and deny-by-default
> unknown paths and explicitly authorized the complete implementation on 2026-07-22. This plan adds
> no behavior beyond that approved contract.

## Summary

Complete the reactive Gateway error boundary without turning it into a downstream business-error
translation hub. Existing Product Admin 400/401/403 responses remain stable. Gateway-owned security,
request-boundary, routed-connection, authentication-infrastructure, and unexpected failures use the
approved seven-code `{code,message,traceId}` contract. Normal downstream HTTP responses, including
4xx and 5xx, continue through Spring Cloud Gateway without deserialization or wrapping.

The implementation stays in ADR 0003's lean technical packages. A trace resolver, conservative
failure classifier, ordered `WebExceptionHandler`, and safe observation component are added. The
existing writer, security handler, and Product Admin boundary filter are narrowed to their actual
responsibilities. No production dependency, route, timeout, retry, rate-limit, circuit-breaker,
database, Kafka, Redis, or deployment configuration is added.

## Technical Context

**Language/Version**: Java 21

**Framework**: Spring Boot 3.5.16, Spring Cloud Gateway Server WebFlux 4.3.5, Spring Security 6.5.11

**Build**: Root Maven wrapper; independently verifiable `services/api-gateway` module

**Primary dependencies**: Existing Gateway WebFlux, Spring Security/OAuth2 Resource Server,
Jackson, Reactor Netty, SLF4J, Spring Boot Test, and Spring Security Test. No addition.

**Storage**: N/A; all classification and correlation state is request-scoped and non-durable

**Communication**: Existing HTTP proxy routes plus the Gateway-owned error contract at
`contracts/gateway-error-http.md`; no Kafka interaction

**Testing**: Focused unit tests, reactive security/component tests, and HTTP proxy contract tests;
module reactor verification through the Maven wrapper

**Target platform**: Local JVM and the existing future Kubernetes deployment model; no manifest delta

**Performance goals**: No new numeric target is approved. Error handling remains non-blocking,
per-request, and performs no network call, retry, persistence, or business fallback.

**Constraints**: Preserve downstream status/body bytes, safe deterministic messages, a non-blank
trace ID on Gateway-owned errors, Bearer challenge semantics on 401, deny-by-default unknown paths,
and no response rewrite after commit

**Scale/scope**: One edge service; only failures owned by `api-gateway`; downstream business-error
standardization remains outside Feature 011

## Risk Classification

| Dimension | Level | Evidence | Required mitigation/verification |
|-----------|-------|----------|----------------------------------|
| Money/payment | Low | FR-003 and FR-012 keep service business outcomes untouched | Representative downstream errors pass through unchanged |
| Inventory/concurrency | Low | No stock, state, or durable mutation | N/A; no concurrency/load test required |
| Security/privacy | High | FR-009, FR-010, and FR-014 change public 401/403/503 handling | Real reactive security tests, Bearer challenge assertion, safe-message leakage checks |
| Distributed consistency | Low | No persistence, messaging, retry, or state transition | N/A |
| Contract/compatibility | High | FR-002, FR-003, FR-006, and the seven-code table | Exact contract tests and downstream byte-for-byte pass-through tests |
| Migration/rollback | Low | Java-only additive behavior; no schema or config change | Module rollback is a code revert |
| Load/operability | Medium | All Gateway-owned errors require correlation evidence | Stable request-scoped trace and one safe structured log per written error |

**Overall risk**: High because the change affects the only public ingress, JWT failure semantics, and
downstream contract compatibility.

**Selected test ordering**: Contract and boundary tests are written or updated before the matching
production changes. Focused tests run after each coherent group, followed by clean module reactor
verification.

**Approval gates**: Approved spec and contract; approved dependency-ordered tasks; consistency
analysis without critical/high gaps; all required Gateway tests and Maven verification passing.

## Constitution Check

*GATE: Checked before research and re-checked after design.*

- **Specification traceability**: PASS. Every production type and test maps to FR-001 through FR-016,
  NFR-001 through NFR-004, or the three user stories; no blocking clarification remains.
- **Service ownership**: PASS. `api-gateway` alone owns edge classification/rendering. No downstream
  service model, exception, database, or deployment lifecycle is imported.
- **Communication**: PASS. Existing public ingress and HTTP routing are retained; the error contract
  is documented before code; no discovery or Kafka change occurs.
- **Data and messaging**: PASS/N/A. No PostgreSQL, Redis, Kafka, transaction, idempotency, outbox,
  ordering, retry, or reconciliation behavior is introduced.
- **Infrastructure ownership**: PASS/N/A. No Docker, Kubernetes, Helm, monitoring, migration, or
  runtime configuration asset changes.
- **Observability**: PASS. Existing Actuator/Prometheus configuration is unchanged. Each written
  Gateway error has stable correlation evidence without constructing a registry.
- **Dependencies/contracts**: PASS. No production dependency is added; the HTTP contract and
  compatibility behavior are explicit.
- **Validation**: PASS. Unit, reactive component/security, proxy contract, and module Maven checks
  apply. Load, database, Kafka, migration, and Kubernetes checks are N/A for this service-local,
  stateless contract slice.

**Gate result**: PASS before and after design

**Violations or waivers**: None. ADR 0003 already authorizes the lean Gateway package profile.

## Context and Service Ownership

| Capability/data | Owner | Readers/callers | Allowed interaction | Prohibited interaction |
|-----------------|-------|-----------------|---------------------|------------------------|
| Gateway-owned HTTP failure contract | `api-gateway` | Public clients and operators | Stable HTTP envelope and safe log correlation | Downstream business-code translation or service model imports |
| Product/Admin HTTP response | `product-service` | Client through Gateway | Transparent HTTP proxy response | Gateway deserialization, wrapping, or guessed Product error code |
| JWT verification outcome | Gateway security boundary using the configured authentication prerequisite | Public clients | 401 for invalid credentials; 503 only for verification infrastructure failure | Token issuance, signing, JWKS rotation, or raw provider detail exposure |

**Context-map change**: None. No bounded-context or service relationship changes.

**Boundary decision**: The work belongs to the public reactive edge because it classifies only
failures produced while securing, validating, or routing the request. Service business failures stay
with their owning service.

## Architecture and Hexagonal Mapping

`api-gateway` uses ADR 0003's responsibility-oriented edge profile rather than empty business
`domain/application/adapter` layers.

| Concern | Planned package/path | Responsibility | Must not depend on |
|---------|----------------------|----------------|--------------------|
| Error taxonomy/wire body | `gateway/error/GatewayErrorCode.java`, `GatewayErrorResponse.java` | Approved edge codes and immutable HTTP body | Service-specific DTOs, enums, exceptions, or domain models |
| Failure classification | `gateway/error/GatewayFailureClassifier.java` | Conservatively map security infrastructure and routed no-response failures | Downstream response body/status or business code |
| Reactive catch-all | `gateway/error/GatewayWebExceptionHandler.java` | Handle uncommitted exceptions before Boot's generic handler; delegate unapproved status exceptions | Controllers, persistence, blocking I/O |
| HTTP rendering | `gateway/error/GatewayHttpErrorWriter.java` | Resolve trace, serialize stable body, use the approved safe internal fallback if serialization fails, set status/content type, and write once | Failure-type guessing or route business behavior |
| Correlation | `gateway/observability/GatewayTraceIdResolver.java` | Normalize a caller trace or keep one generated ID per exchange | Service business logic or global mutable state |
| Safe operator evidence | `gateway/observability/GatewayErrorObservation.java` | Emit one parameterized record with code, trace, method, path, and cause class only; reversibly escape untrusted control characters at the logger boundary | Bearer token, body, raw exception message, provider URL |
| Security translation | `gateway/security/GatewaySecurityConfiguration.java`, `GatewaySecurityErrorHandler.java` | Keep only configured Actuator endpoints public; distinguish 401, auth-infrastructure 503, route-specific 403, and generic 403 | Authentication-service implementation or issued-token logic |
| Admin request boundary | `gateway/filter/global/AdminCatalogRequestBoundaryFilter.java` | Validate/normalize the caller trace and remove caller-controlled actor header | Error serialization and trace generation policy |

**Dependency direction check**: Technical adapters depend only on other Gateway edge components.
No service or shared business module is added. Tests mirror the owning package and verify that
classification never inspects normal downstream response content.

### Approved Classification Allow-Lists and Precedence

Classification walks the complete cause chain with identity-based cycle protection. It uses this
order; later rules cannot override an earlier one:

1. committed response -> propagate without classification or write;
2. explicit `ResponseStatusException` -> delegate to the next framework handler;
3. authentication-infrastructure failure -> `AUTHENTICATION_UNAVAILABLE`;
4. selected HTTP(S) route plus no downstream response plus approved transport cause ->
   `DOWNSTREAM_UNAVAILABLE`;
5. everything else -> `GATEWAY_INTERNAL_ERROR`.

Inside `GatewaySecurityErrorHandler`, authentication infrastructure is exactly a cause chain
containing at least one of:

- `org.springframework.security.authentication.AuthenticationServiceException`;
- `org.springframework.security.oauth2.jwt.JwtDecoderInitializationException`;
- `org.springframework.web.reactive.function.client.WebClientRequestException`;
- `org.springframework.web.reactive.function.client.WebClientResponseException`;
- `java.net.ConnectException`, `java.net.UnknownHostException`,
  `java.net.SocketTimeoutException`, or `java.nio.channels.UnresolvedAddressException`;
- `io.netty.channel.ConnectTimeoutException`, `io.netty.handler.timeout.ReadTimeoutException`, or
  `reactor.netty.http.client.PrematureCloseException`;
- a cause whose qualified class name is `com.nimbusds.jose.RemoteKeySourceException`, recognized by
  name to avoid a direct dependency on the transitive Nimbus provider library.

This allow-list applies to transport/provider causes only in the authentication context. A generic
`JwtException`, `BadJwtException`, `InvalidBearerTokenException`, or
`OAuth2AuthenticationException` without one of those nested causes remains `UNAUTHENTICATED`.

When authentication failures escape to the global handler, only the high-level
`AuthenticationServiceException`, `JwtDecoderInitializationException`, or qualified
`RemoteKeySourceException` markers establish authentication context; a raw transport exception does
not.

For a routed downstream exchange, the approved transport allow-list is exactly:

- `java.net.ConnectException`;
- `java.net.UnknownHostException`;
- `java.nio.channels.UnresolvedAddressException`;
- `io.netty.channel.ConnectTimeoutException` (failure to establish the connection, reported as the
  approved generic 503 rather than a new timeout code);
- `reactor.netty.http.client.PrematureCloseException`, only while no downstream response attribute
  exists and the response is uncommitted.

The routed classifier explicitly excludes `ReadTimeoutException`, `SocketTimeoutException`, generic
`IOException`, `ClosedChannelException`, Reactor Netty client-abort types, and every
`ResponseStatusException`. It additionally requires `GATEWAY_ROUTE_ATTR`, an HTTP(S)
`GATEWAY_REQUEST_URL_ATTR`, absent `CLIENT_RESPONSE_ATTR` and `CLIENT_RESPONSE_CONN_ATTR`, and an
uncommitted server response. This freezes the 401/503 security decision and prevents downstream
timeouts, client disconnects, partial responses, or deliberate downstream 4xx/5xx from being
misclassified.

## Synchronous and Asynchronous Flows

### Flow 1 - Security or request-boundary rejection

1. Spring Security or the Product Admin boundary filter rejects the request.
2. The security handler or filter selects one approved code.
3. The writer resolves a valid caller trace or a stable per-exchange generated trace.
4. One safe operator record is emitted and one JSON response is written.
5. A 401 includes `WWW-Authenticate: Bearer`; a 503 does not claim credentials are invalid.

**Ordering guarantee**: One error response and one observation per successful writer invocation

**Timeout/retry behavior**: No retry or new timeout

**Duplicate/replay behavior**: N/A; no durable effect. Repeated trace header values preserve the
existing first-value normalization behavior; invalid first values generate an error correlation ID.

### Flow 2 - Routed downstream exchange fails before a response

1. A declarative route selects an HTTP(S) downstream target.
2. The exchange fails with a whitelisted connection/DNS/premature-close cause before any downstream
   response attribute exists.
3. The ordered WebExceptionHandler verifies routing context and an uncommitted response.
4. It selects `DOWNSTREAM_UNAVAILABLE` and delegates rendering to the writer.
5. No internal address, port, exception message, or stack trace reaches the client.

**Ordering guarantee**: Auth-infrastructure classification takes precedence over generic routed
network classification.

**Timeout/retry behavior**: No retry. Explicit timeout taxonomy remains deferred; an existing
`ResponseStatusException` is delegated to the next framework handler.

**Duplicate/replay behavior**: N/A

### Flow 3 - Downstream service returns an HTTP error

1. The route obtains a downstream response, including 400, 404, 409, or 500.
2. Spring Cloud Gateway's normal response filter writes the downstream status, headers, and bytes.
3. Feature 011 handlers are not invoked and do not inspect or translate the body.

**Sequence/ordering guarantee**: The obtained response remains service-owned.

**Timeout/retry behavior**: No retry or fallback

**Duplicate/replay behavior**: Defined only by the downstream contract, not this feature

### Flow 4 - Unexpected Gateway exception

1. An uncommitted exception escapes the Gateway filter chain and is not an explicit
   `ResponseStatusException`.
2. The classifier selects `GATEWAY_INTERNAL_ERROR` when no approved specific category matches.
3. The writer returns only the stable safe message and trace correlation.
4. If the response is already committed, the handler propagates the original failure without a
   second write.
5. If normal Jackson serialization fails before commit, the writer uses a minimal fixed encoder for
   the safe `GATEWAY_INTERNAL_ERROR` envelope and records only the serialization cause class.

## Data, Transactions, and Concurrency

- **Source of truth**: N/A
- **Transaction boundary**: N/A
- **Hot-path state**: N/A
- **Concurrency control**: Stateless components plus one exchange attribute for the generated trace;
  no shared mutable request state
- **Idempotency record**: N/A
- **Outbox/inbox**: N/A
- **Migration**: N/A

See [data-model.md](data-model.md) for the transient wire and correlation models.

## Contracts and Compatibility

| Contract | Owner | Consumers | Version/change | Compatibility and rollout |
|----------|-------|-----------|----------------|---------------------------|
| Gateway-owned HTTP error | `api-gateway` | Public clients | Additive four-code extension to the existing three-field body | Existing Product Admin codes/messages/body shape remain unchanged |
| Downstream HTTP response pass-through | Owning downstream service | Public client through Gateway | No downstream contract change | Status/body bytes are preserved; Gateway never imports service error types |

**Contract file path**: `specs/011-gateway-error-handling/contracts/gateway-error-http.md`

**Breaking-change decision**: None. The blank/absent invalid Product Admin trace response changes
only from nullable to generated non-blank `traceId`, as explicitly approved by FR-008.

## Failure, Retry, and Compensation

| Failure mode | Detection | Client outcome | Retry/compensation | Recovery | Evidence |
|--------------|-----------|----------------|--------------------|----------|----------|
| Invalid/expired/malformed credentials | Authentication failure without infrastructure cause | 401 `UNAUTHENTICATED` + Bearer challenge | None | Client obtains valid credentials | Security component/HTTP test |
| JWT/JWKS verification infrastructure unavailable | Auth-context cause-chain marker | 503 `AUTHENTICATION_UNAVAILABLE` | None | Operator restores prerequisite | Security tests with nested transport cause |
| Product Admin trace invalid | Boundary normalization returns absent | 400 `INVALID_ADMIN_REQUEST` with generated error trace | None | Client corrects header | Existing route tests updated |
| Authenticated Product Admin lacks authority | Admin route plus access denied | 403 `CATALOG_ADMIN_REQUIRED` | None | Permission correction | Security route test |
| Authenticated unknown path | Deny-all path plus access denied | 403 `ACCESS_DENIED` | None | None; no route disclosure | Unknown-path test |
| Unknown path under the Actuator namespace | Not one of the explicitly public operational endpoints | 401 anonymous / 403 authenticated | None | None; no route disclosure | Actuator namespace security test |
| Downstream returns 4xx/5xx | Normal downstream response exists | Preserve downstream status/body | Gateway adds none | Downstream-owned | Proxy contract tests |
| Routed connection/exchange fails before response | Route context + no response attrs + whitelisted cause | 503 `DOWNSTREAM_UNAVAILABLE` | None | Operator restores downstream | Connection-refused test |
| Explicit framework status failure outside taxonomy | `ResponseStatusException` | Delegate to the next handler | Feature 011 adds none | Capability owner | Delegation unit test |
| Unexpected uncommitted Gateway error | No specific category | 500 `GATEWAY_INTERNAL_ERROR` | None | Operator uses trace evidence | Catch-all/leakage test |
| Normal error serialization fails before commit | `JsonProcessingException` while creating the envelope | Minimal 500 `GATEWAY_INTERNAL_ERROR` envelope | None | Operator uses trace and safe cause class | Writer fallback test |
| Error after response commit | `response.isCommitted()` | Preserve partial/original response; propagate | None | Transport/client recovery | Committed-response test |

No compensation or reconciliation applies because this feature performs no business mutation.

## Security and Abuse Controls

- **Authentication/authorization**: Existing public catalog and `CATALOG_ADMIN` route rules remain.
  Invalid credentials stay 401; verification infrastructure failure is 503; authenticated unknown
  paths use generic 403.
- **Trust boundaries**: Caller `X-Trace-Id` is trimmed and bounded at 128 characters. Product Admin
  still rejects a missing/blank/overlong first value; error correlation generation does not bypass
  that rejection.
- **Sensitive data**: Client messages and safe observation fields exclude tokens, request bodies,
  exception messages, internal hosts/ports, signing/JWKS details, and provider responses.
- **Abuse controls**: Rate limiting, quotas, circuit breaking, fallback, and retry are out of scope.
- **Auditability**: One Gateway-owned error observation contains code, HTTP status, trace ID, method,
  path, and cause class when available. No retention policy is introduced.

## Observability and Operations

- **Health**: Existing liveness/readiness endpoints are unchanged; no new dependency health check.
- **Metrics**: Existing declarative `/actuator/prometheus` exposure remains; no metric or registry code.
- **Tracing**: Valid caller trace is preserved; otherwise one UUID is generated and reused for the
  Gateway-owned error and its observation.
- **Logs**: Parameterized safe fields only: error code/status, trace ID, method, path, cause class.
- **Alerts/SLOs**: No threshold or alert is approved in this feature.
- **Runbook/reconciliation**: Operators use `traceId` to locate the Gateway error record; no data
  reconciliation applies.

## Migration, Rollout, and Rollback

1. Add contract and tests for the seven codes, trace, security, and pass-through rules.
2. Add trace/classification/observation components and update writer/security/filter wiring.
3. Run focused tests and clean module reactor verification.
4. Deploy the independently built Gateway image through the existing environment process and verify
   representative 400/401/403/500/503 scenarios.

**Rollback trigger**: Any existing Product Admin contract regression, missing Bearer challenge,
downstream response rewrite, or required test/build failure

**Rollback procedure**: Revert the Feature 011 Java/test changes and redeploy the prior Gateway image;
no schema, message, or configuration rollback is needed.

**Irreversible step**: None

## Verification Strategy and Evidence

| Requirement/risk | Verification type | Command/environment | Expected evidence | Owner |
|------------------|-------------------|---------------------|-------------------|-------|
| FR-001/FR-006/FR-007 exact taxonomy | Unit/contract | Gateway targeted tests | Seven exact status/code/message mappings and three-field body | Gateway owner |
| FR-008/NFR-004 trace behavior | Unit/component | Trace resolver, writer, and Product Admin tests | Valid trace preserved; absent/invalid trace generated and non-blank | Gateway owner |
| FR-009/FR-010 security | Reactive component/HTTP | Security handler and root/Actuator unknown-path tests | Correct 401/403 plus Bearer challenge, configured health remains public, and no route detail | Security reviewer |
| FR-014 auth infrastructure distinction | Reactive security component | Nested invalid-token and infrastructure-failure tests | Invalid token 401; verification outage 503 | Security reviewer |
| FR-003/FR-004/FR-015 compatibility | HTTP proxy contract | Test upstream and closed-port scenarios | Downstream 400/404/409/500 bytes unchanged; no-response network failure 503 | Client-contract reviewer |
| FR-005/FR-016/NFR-003 safe catch-all | Unit/component | Writer/global-handler leakage and serialization-fallback tests | Stable 500 and no raw token/body/host/message in response | Gateway owner |
| FR-011 committed response | Unit/component | Writer/global-handler committed exchange tests | No second write; original failure propagated | Gateway owner |
| Constitution module gate | Maven reactor | `.\mvnw.cmd -pl services/api-gateway -am clean verify` | Exit 0 and all affected tests pass | Gateway owner |

**Required module build**: `.\mvnw.cmd -pl services/api-gateway -am clean verify`

**Required full build**: N/A; no root build configuration, shared library, or cross-service production
code changes

**Kubernetes validation**: N/A; no manifest or overlay changes

**Load/failure validation**: Contract-level failure injection applies. A load test is N/A because no
throughput/latency policy or stateful hot path changes.

Validation command, scope, exit status, and concise test result are recorded in `validation.md`
before tasks are marked complete.

## Project Structure

### Documentation

```text
specs/011-gateway-error-handling/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── validation.md
├── contracts/
│   └── gateway-error-http.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code

```text
services/api-gateway/src/main/java/com/philia/flashsale/gateway/
├── error/
│   ├── GatewayErrorCode.java
│   ├── GatewayErrorResponse.java
│   ├── GatewayFailureClassifier.java
│   ├── GatewayHttpErrorWriter.java
│   └── GatewayWebExceptionHandler.java
├── filter/global/
│   └── AdminCatalogRequestBoundaryFilter.java
├── observability/
│   ├── GatewayErrorObservation.java
│   └── GatewayTraceIdResolver.java
└── security/
    ├── GatewaySecurityConfiguration.java
    └── GatewaySecurityErrorHandler.java

services/api-gateway/src/test/java/com/philia/flashsale/gateway/
├── error/
├── observability/
├── security/
├── ProductAdminGatewayRouteTests.java
├── GatewayUnknownPathSecurityTests.java
├── GatewayAuthenticationFailureContractTests.java
├── GatewayProxyPassThroughTests.java
├── GatewayDownstreamUnavailableTests.java
└── GatewayUnexpectedFailureTests.java
```

**Structure decision**: Keep the accepted ADR 0003 lean edge organization. Add only packages with a
real Feature 011 responsibility, mirror them in tests, and do not create business layers or a shared
Gateway/service error model.

## Complexity Tracking

No constitutional violation, exception, new dependency, or ADR change is required.
