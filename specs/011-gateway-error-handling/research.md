# Research: Gateway-Owned Error Handling

**Feature**: `011-gateway-error-handling`
**Date**: 2026-07-22
**Status**: Complete

## Decision 1 — Keep downstream HTTP responses outside Gateway error classification

**Decision**: A normal downstream response, including 4xx or 5xx, stays on Spring Cloud Gateway's
normal response-writing path. Feature 011 does not scan response status, parse error JSON, or wrap
the body. The global handler is invoked only for exceptions that escape the reactive chain.

**Rationale**: Spring Cloud Gateway 4.3.5 records an obtained Netty client response on the exchange
and writes it through its response filter. Treating every status `>= 400` as a Gateway exception
would transfer downstream contract ownership to the edge and violate FR-003.

**Alternatives considered**:

- Wrap all 4xx/5xx in one common response: rejected because it tightly couples the Gateway to every
  service contract and destroys byte-for-byte compatibility.
- Translate selected Product errors only: rejected because Product business failures remain
  product-service-owned.

## Decision 2 — Classify routed unavailability with context plus a narrow cause allow-list

**Decision**: `DOWNSTREAM_UNAVAILABLE` requires all of the following:

1. a Gateway route was selected;
2. the selected request URI uses HTTP or HTTPS;
3. no downstream response/connection attribute was obtained;
4. the response is not committed; and
5. the exception cause chain contains a recognized connection, DNS-resolution, or premature-close
   failure.

The exact routed allow-list is `ConnectException`, `UnknownHostException`,
`UnresolvedAddressException`, Netty `ConnectTimeoutException`, and Reactor Netty
`PrematureCloseException` before a response exists. The classifier excludes read/socket timeouts,
generic I/O, closed-channel/client-abort failures, and every `ResponseStatusException`. The complete
qualified-name list and precedence are canonical in `plan.md` under **Approved Classification
Allow-Lists and Precedence**.

An unmatched exception becomes `GATEWAY_INTERNAL_ERROR`. An explicit `ResponseStatusException` is
delegated to the next framework handler because timeout and other status-specific capabilities are
not approved in this slice.

**Rationale**: A raw `ConnectException` can originate from another edge dependency or filter. Route
context prevents the Gateway from incorrectly blaming the selected service. Response attributes
separate “service returned 500” from “no HTTP response was obtained.”

**Alternatives considered**:

- Exception type alone: rejected because it can misclassify pre-route or authentication failures.
- Status-based classification: rejected because normal service 4xx/5xx are not Gateway failures.
- Broad `IOException` mapping: rejected because client disconnects and unrelated I/O would be
  reported as downstream unavailability.

## Decision 3 — Distinguish invalid JWT from verification-infrastructure failure by context

**Decision**: The security entry point asks `GatewayFailureClassifier` to classify an authentication
failure. Invalid, malformed, expired, or bad-signature credentials remain `UNAUTHENTICATED`. A cause
chain containing authentication-infrastructure markers or transport/provider-access failures in the
authentication context becomes `AUTHENTICATION_UNAVAILABLE`.

The global handler also recognizes stable high-level authentication-infrastructure markers before
routed-network classification. The implementation does not import authentication-service classes or
the authentication service's business model.

In the authentication-handler context, the exact allow-list is Spring
`AuthenticationServiceException`, `JwtDecoderInitializationException`, WebClient request/response
failures, connection/DNS/socket timeout causes, Netty connect/read timeout, Reactor Netty premature
close, and Nimbus `RemoteKeySourceException`. Outside that security context, only the high-level
authentication markers establish that a transport cause belongs to JWT/JWKS verification. Generic
JWT/authentication exceptions without an approved nested infrastructure cause remain 401. The full
qualified-name list and precedence are frozen in `plan.md`.

**Rationale**: Spring Security's reactive JWT authentication manager can wrap decoder failures in an
`InvalidBearerTokenException` while preserving the original cause. Inspecting only the top-level
exception would incorrectly tell clients that a valid token is bad when JWKS verification is down.

**Alternatives considered**:

- Map every `JwtException` to 503: rejected because ordinary invalid tokens also use `JwtException`.
- Always return 401: rejected by approved FR-014.
- Add a custom authentication-service client or decoder dependency: rejected as unnecessary
  coupling for the current cause-chain evidence.

**Caveat**: Provider libraries do not expose one perfectly stable exception for every malformed JWKS
case. Classification therefore prefers Spring-level and transport-level causes. Tests freeze the
behavior against the repository's approved Spring Boot/Security versions.

## Decision 4 — Use an ordered reactive exception handler, not controller advice

**Decision**: Add a `WebExceptionHandler` ordered at `-2`, before Spring Boot's default reactive
handler. It delegates explicit `ResponseStatusException` values, propagates a failure when the
response is already committed, and otherwise sends an approved code to the writer.

**Rationale**: Gateway routing happens in WebFlux filters/WebHandler processing, not an annotated
controller. A controller advice would not be the correct catch-all boundary.

**Alternatives considered**:

- `@ControllerAdvice`: rejected because the Gateway exposes no controller for this flow.
- One broad global filter with `onErrorResume`: rejected because security errors and framework error
  ordering would remain split and it would be easier to wrap obtained downstream responses.

## Decision 5 — Separate trace normalization/generation from error serialization

**Decision**: `GatewayTraceIdResolver` owns caller trace normalization and stores one generated UUID
as an exchange attribute when a usable first header value is absent. The error writer reuses that
value. The Product Admin boundary uses normalization only, so it can still reject a missing/blank/
overlong value while the rejection response receives a generated correlation ID.

Multiple header values preserve existing compatibility: the first header value is canonical for
normalization. An invalid first value is not rescued by a later value.

**Rationale**: The current writer both normalizes trace input and serializes HTTP output. Separating
these concerns makes the filter, writer, and tests independent while guaranteeing a stable trace
within one exchange.

**Alternatives considered**:

- Nullable trace on errors: rejected by approved Option A and NFR-004.
- Require a caller trace globally: rejected by approved Option A.
- Reject every repeated header now: not selected because it would be a new compatibility delta not
  required by the approved feature.

If normal Jackson serialization fails before commit, the writer uses Jackson Core string escaping
only to emit the fixed `GATEWAY_INTERNAL_ERROR` envelope. This prevents recursion through the normal
mapper and preserves the approved body/trace contract without adding a dependency.

## Decision 6 — Record one safe operator event at the writer boundary

**Decision**: `GatewayErrorObservation` emits one parameterized warning when the writer owns a
failure. Fields are code/status, trace ID, method, path, and cause class when present. It never logs
the bearer token, request body, raw exception message, internal destination, or provider payload.
Caller trace/path values are quoted and reversibly escaped at the final logger boundary so CR/LF,
ISO control characters, and Unicode line separators cannot forge additional log records.

**Rationale**: Every Gateway-owned error reaches the writer, while downstream service errors do not.
This gives one correlation record without duplicating logging in security, filters, and the global
handler.

**Alternatives considered**:

- Log independently in each producer: rejected because it creates inconsistent fields and duplicate
  records.
- Log raw exception messages/stack traces at warning level: rejected for the client-safe operational
  slice; deeper diagnostics can be added later under an approved logging policy.

## Decision 7 — No new production dependency or configuration

**Decision**: Use existing Spring WebFlux/Security/Gateway, Jackson, Reactor Netty, SLF4J, Java UUID,
and test libraries. Keep declarative routes, JWT/JWKS URI, Actuator, and Prometheus configuration
unchanged.

**Rationale**: All required capabilities already exist. Adding common-web would couple this local
edge contract to service response models, while rate-limit/circuit/timeout dependencies would exceed
the approved slice.

## Version Evidence

| Component | Repository-resolved version |
|-----------|-----------------------------|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring Cloud BOM | 2025.0.3 |
| Spring Cloud Gateway Server WebFlux | 4.3.5 |
| Spring Security | 6.5.11 |
| Reactor Netty | 1.2.18 |

No `NEEDS CLARIFICATION` item remains after this research.
