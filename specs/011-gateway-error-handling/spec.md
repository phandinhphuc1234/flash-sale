# Feature Specification: Gateway-Owned Error Handling

**Feature Branch**: `011-gateway-error-handling`
**Created**: 2026-07-22
**Status**: Verified
**Owner**: Gateway/Platform owner (user)
**Reviewers**: Security owner, API Gateway technical owner, client-contract reviewer
**Input**: User description: "Complete api-gateway error responses so failures created by the gateway are returned by the gateway, while downstream service errors remain owned by each service; keep the design loosely coupled."

> This feature uses the Flash Sale risk profile because it changes security, compatibility,
> availability-failure, and trace-correlation behavior at the only public ingress. It preserves
> downstream service ownership and must not turn the gateway into a service-error coupling hub.

## Clarifications

### Session 2026-07-22

- Q: Which additional gateway failures and status policy are implemented now? → A: Option A — add
  downstream/authentication unavailability as 503 and unexpected gateway failure as 500; defer
  timeout, no-route, rate-limit, and circuit-breaker policies.
- Q: What trace behavior applies when the caller trace is absent or invalid? → A: Option A — use a
  generated server correlation ID in the gateway-owned error while preserving any route-specific
  validation rejection.
- Q: What should unknown paths reveal? → A: Option A — preserve deny-by-default security; return
  `UNAUTHENTICATED` to anonymous callers and generic `ACCESS_DENIED` to authenticated callers without
  revealing whether a route exists.

## Problem and Scope

**Business problem**: Clients currently receive a stable body for three Product Admin edge failures,
but other failures originating at the gateway can fall back to framework-specific responses. This
makes client handling and incident diagnosis inconsistent, while indiscriminate response wrapping
would couple the gateway to every downstream service's error model.

**Goal**: Give clients a stable, safe, traceable response for every gateway-owned failure included
in the approved first slice, while forwarding downstream HTTP responses without translating their
business error codes or bodies.

**In scope**:

- Preserve the existing Product Admin gateway outcomes `INVALID_ADMIN_REQUEST`, `UNAUTHENTICATED`,
  and `CATALOG_ADMIN_REQUIRED`.
- Define a stable response contract for the additional gateway-owned failures selected for the
  first slice.
- Distinguish a downstream HTTP error response from a gateway failure to reach or process the
  downstream exchange.
- Return client-safe messages without exposing exception classes, stack traces, internal hosts, or
  signing/provider details.
- Preserve or establish trace correlation for gateway-owned failures according to the approved
  trace policy.
- Verify that downstream 4xx and 5xx status/body content passes through unchanged.

**Out of scope**:

- Standardizing error contracts inside product-service or any other downstream service.
- Translating `PRODUCT_NOT_FOUND`, duplicate identifiers, lifecycle conflicts, payment failures,
  stock failures, or any other service-owned business outcome.
- Implementing retries, fallback business data, compensation, service discovery, or another
  service's availability policy.
- Implementing rate limiting or circuit breaking before those capabilities have their own approved
  limits and failure policies.
- Login, token issuance, JWT signing keys, JWKS rotation, refresh, or revocation behavior.
- Replacing the common response contract of every microservice.

## Baseline References and Requirement Delta

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [Feature 010 admin HTTP contract](../010-catalog-administration/contracts/product-catalog-admin-http.md) | Existing `{code,message,traceId}` body and the three Product Admin edge codes | Preserve the wire shape and existing outcomes unless an explicitly approved compatibility decision says otherwise |
| [Feature 010 plan](../010-catalog-administration/plan.md) | Gateway owns only three current edge failures; downstream Product failures pass through | Add a separately approved gateway-wide failure slice without changing Product ownership |
| [ADR 0003](../../docs/adr/0003-lean-api-gateway-package-structure.md) | Lean technical packages for gateway security, filtering, errors, fault tolerance, rate limiting, and observability | Add real types only where the approved failure slice creates a concrete responsibility |
| Current gateway implementation | Security and admin-boundary failures already use a gateway-local writer | Remove inconsistent framework fallback only for approved gateway-owned failures |

## User Scenarios & Testing

### User Story 1 - Receive a stable gateway failure (Priority: P1)

As an API client, I want failures produced before or during gateway routing to use one stable,
client-safe response so that I can handle them predictably and report a trace identifier to support.

**Why this priority**: The gateway is the only public ingress; inconsistent edge failures affect
every client and make security or availability incidents difficult to diagnose.

**Independent Test**: Trigger each approved gateway-owned failure without invoking downstream
business behavior and verify its status, stable code, safe message, content type, and trace outcome.

**Use-case references**: UC-GW-ERR-01 Return Gateway-Owned Failure

**Acceptance Scenarios**:

1. **Given** a request rejected by gateway authentication, authorization, or an approved request
   boundary, **When** the rejection occurs, **Then** the gateway returns the stable gateway error
   response and does not invoke a downstream business use case.
2. **Given** the gateway cannot complete an approved infrastructure exchange, **When** the failure
   matches an approved gateway category, **Then** the gateway returns that category's stable status,
   code, safe message, and trace outcome.
3. **Given** an unexpected gateway exception, **When** the approved catch-all policy applies,
   **Then** the response contains no internal exception or infrastructure details.
4. **Given** an unknown path, **When** an anonymous or authenticated caller requests it, **Then** the
   gateway preserves deny-by-default security and returns `UNAUTHENTICATED` or `ACCESS_DENIED`
   respectively without disclosing route existence.

---

### User Story 2 - Preserve downstream service ownership (Priority: P2)

As a downstream service owner, I want my service's HTTP error response to pass through the gateway
without being deserialized or reclassified so that my service remains the sole owner of its business
error contract.

**Why this priority**: Rewrapping every downstream error would tightly couple the gateway to all
services and make independent service contract evolution unsafe.

**Independent Test**: Use an isolated downstream that returns representative 400, 404, 409, and 500
responses and verify that the gateway preserves the application status and body byte-for-byte.

**Use-case references**: UC-GW-ERR-02 Forward Downstream Outcome

**Acceptance Scenarios**:

1. **Given** a matched route and a downstream service response with status 4xx, **When** the gateway
   receives that response normally, **Then** it forwards the downstream status and body without
   replacing them with a gateway error.
2. **Given** a matched route and a downstream service response with status 5xx, **When** the gateway
   receives that response normally, **Then** it forwards the downstream status and body without
   claiming the failure as gateway-owned.
3. **Given** no HTTP response was obtained because the gateway exchange itself failed, **When** the
   failure is classified, **Then** the gateway may return only an approved gateway-owned code and
   never a guessed service business code.

---

### User Story 3 - Diagnose a gateway failure safely (Priority: P3)

As an operator, I want every gateway-owned failure to have usable correlation evidence while client
messages remain safe so that I can investigate incidents without leaking internal details.

**Why this priority**: A stable client body is insufficient if the failure cannot be correlated with
gateway logs and downstream attempts.

**Independent Test**: Trigger approved failures with valid, missing, blank, oversized, and repeated
trace inputs, then verify the approved client trace behavior and one correlatable operator record per
failure without sensitive request/token content.

**Use-case references**: UC-GW-ERR-03 Correlate Gateway Failure

**Acceptance Scenarios**:

1. **Given** a valid caller trace identifier, **When** a gateway-owned failure occurs, **Then** the
   same normalized identifier is available in the client error and operator correlation evidence.
2. **Given** an absent or invalid caller trace identifier, **When** a gateway-owned failure occurs,
   **Then** the gateway returns a generated server correlation ID; a route that requires a valid
   caller identifier may still reject the request using its approved gateway error.
3. **Given** a gateway-owned failure, **When** it is recorded for operations, **Then** no bearer token,
   full request body, stack trace, or raw provider exception is returned to the client.

### Edge Cases and Failure Outcomes

- A request is both unauthenticated and missing a required Product Admin trace header.
- The response is already committed when a later reactive failure occurs.
- The downstream deliberately returns 500 versus the gateway failing before any downstream response.
- A caller supplies a blank, whitespace-padded, 128-character, or oversized trace identifier.
- An unknown path is requested by an unauthenticated or authenticated caller.
- Serialization of the normal error body itself fails before commit; the gateway returns the safe
  `GATEWAY_INTERNAL_ERROR` envelope through a minimal fallback encoder using the same resolved trace.
- A client disconnects while the gateway is writing the error response.
- Multiple failures are wrapped in nested reactive/network exceptions.

## Requirements

### Functional Requirements

- **FR-001**: The gateway MUST return one stable error envelope for every gateway-owned failure in
  the approved first-slice taxonomy.
- **FR-002**: The gateway MUST preserve the existing `INVALID_ADMIN_REQUEST`, `UNAUTHENTICATED`, and
  `CATALOG_ADMIN_REQUIRED` observable outcomes for Product Admin clients unless a separately approved
  compatibility delta explicitly changes one.
- **FR-003**: A downstream HTTP 4xx or 5xx response obtained normally MUST retain its application
  status and body; the gateway MUST NOT deserialize it, translate its business code, or wrap it in a
  gateway response.
- **FR-004**: The gateway MUST distinguish an obtained downstream HTTP response from failure of the
  gateway exchange itself.
- **FR-005**: Gateway-owned messages MUST be deterministic and client-safe and MUST NOT expose raw
  exception messages, class names, stack traces, internal hostnames, downstream addresses, signing
  keys, tokens, or provider details.
- **FR-006**: A gateway-owned error MUST use the existing `{code,message,traceId}` envelope for
  backward compatibility; downstream service envelopes remain outside this feature.
- **FR-007**: The approved first-slice taxonomy MUST retain the existing three Product Admin codes
  and add `ACCESS_DENIED` with status 403, `DOWNSTREAM_UNAVAILABLE` with status 503,
  `AUTHENTICATION_UNAVAILABLE` with status 503, and `GATEWAY_INTERNAL_ERROR` with status 500.
  Timeout, route-not-found, rate-limit, and circuit-breaker behavior remain deferred and MUST NOT be
  inferred in this feature.
- **FR-008**: Every gateway-owned error MUST contain a non-blank trace identifier. The gateway MUST
  preserve a valid normalized caller identifier and MUST generate a server correlation identifier
  when the caller value is absent or invalid. Generation MUST NOT bypass a route-specific rule that
  rejects the missing or invalid caller identifier.
- **FR-009**: Unknown paths MUST preserve deny-by-default security and MUST NOT expose route
  existence. An anonymous request MUST receive `UNAUTHENTICATED`; an authenticated request MUST
  receive the generic `ACCESS_DENIED` response rather than the route-specific
  `CATALOG_ADMIN_REQUIRED` response.
- **FR-010**: A 401 gateway response MUST retain the applicable authentication challenge semantics
  while using the approved safe response envelope.
- **FR-011**: The gateway MUST NOT attempt to rewrite an error after the response has already been
  committed.
- **FR-012**: The feature MUST NOT introduce a dependency on downstream service-specific error enums,
  exception types, DTOs, JPA models, or business-domain libraries.
- **FR-013**: Rate-limit, circuit-breaker, retry, fallback, and timeout-duration policies MUST NOT be
  inferred by implementation; each may be added only when selected and approved in this or a later
  feature.
- **FR-014**: Invalid, malformed, expired, or otherwise unverifiable caller credentials MUST remain
  `UNAUTHENTICATED`; only a failure of the authentication verification infrastructure itself may use
  `AUTHENTICATION_UNAVAILABLE`.
- **FR-015**: `DOWNSTREAM_UNAVAILABLE` MUST be used only when routing selected a downstream service
  but the gateway could not obtain an HTTP response because the connection or exchange failed. An
  HTTP 500 response received from that service remains service-owned under FR-003.
- **FR-016**: A gateway exception not matched by a more specific approved category MUST use
  `GATEWAY_INTERNAL_ERROR` and MUST retain its diagnostic cause only in operator-side evidence,
  except an explicit framework `ResponseStatusException` whose status policy is deferred by FR-007;
  that exception MUST be delegated without being claimed by the Feature 011 taxonomy. Failure of
  normal error-body serialization before commit MUST use a minimal safe `GATEWAY_INTERNAL_ERROR`
  fallback rather than a framework-specific body.

### Approved Gateway Error Contract

| Code | Status | Safe message | Additional semantics |
|------|--------|--------------|----------------------|
| `INVALID_ADMIN_REQUEST` | 400 | `X-Trace-Id must be non-blank and no longer than 128 characters` | Product Admin request-boundary failure |
| `UNAUTHENTICATED` | 401 | `Authentication is required` | Include the applicable `WWW-Authenticate: Bearer` challenge |
| `CATALOG_ADMIN_REQUIRED` | 403 | `CATALOG_ADMIN authority is required` | Only for the Product Admin route family |
| `ACCESS_DENIED` | 403 | `Access is denied` | Generic authenticated deny-by-default outcome outside Product Admin routes |
| `DOWNSTREAM_UNAVAILABLE` | 503 | `The requested service is temporarily unavailable` | No downstream HTTP response was obtained |
| `AUTHENTICATION_UNAVAILABLE` | 503 | `Authentication is temporarily unavailable` | Verification infrastructure failed; invalid credentials remain 401 |
| `GATEWAY_INTERNAL_ERROR` | 500 | `The gateway could not process the request` | Safe catch-all for unmatched gateway exceptions |

Every row uses the `{code,message,traceId}` envelope and a non-blank trace identifier. No row
authorizes rewriting an HTTP response already obtained from a downstream service.

### Non-Functional Requirements

- **NFR-001**: One hundred percent of tested gateway-owned failure categories MUST return the
  approved status, code, safe message, content type, and trace outcome.
- **NFR-002**: One hundred percent of representative downstream 400, 404, 409, and 500 contract-test
  responses MUST preserve their status and body through the gateway.
- **NFR-003**: No tested gateway error response may contain a bearer token, raw request body, stack
  trace, internal address, or raw exception message.
- **NFR-004**: Every gateway-owned failure in the approved slice MUST be correlatable using the
  approved trace policy.

### Business Rules and Invariants

- **INV-001**: An HTTP response produced by a downstream service remains owned by that service even
  when its status is 4xx or 5xx.
- **INV-002**: Only a failure produced by gateway security, request-boundary enforcement, routing, or
  an approved gateway infrastructure exchange may use a gateway-owned error code.
- **INV-003**: Client-visible messages never expose internal diagnostic details; detailed cause
  information remains operator-only.
- **INV-004**: A single failure produces at most one client error response.

## Distributed-System Risk Decisions

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | N/A: the gateway does not classify or modify payment business outcomes in this feature. | FR-003, FR-012 |
| Inventory/oversell | N/A: stock and reservation failures remain service-owned and pass through as downstream responses. | FR-003, INV-001 |
| Concurrency | No durable mutation is introduced; concurrent requests are classified independently and must not share mutable per-request error state. | FR-001, INV-004 |
| Idempotency/deduplication | N/A: error rendering creates no durable business effect and must not reinterpret downstream idempotency outcomes. | FR-003, FR-012 |
| Consistency/ordering | Downstream status/body ownership is preserved; no event or cross-request ordering is introduced. | FR-003, INV-001 |
| Retry/timeout/compensation | No retry or compensation is introduced. Timeout classification and duration are explicitly deferred. | FR-007, FR-013 |
| Security/authorization | Existing 401/403 Product Admin behavior remains; unknown paths stay deny-by-default and use authentication-sensitive generic outcomes without exposing route existence. Safe messages and authentication challenge semantics are required. | FR-002, FR-005, FR-009, FR-010 |
| TTL/quota/retention | N/A for the first slice: no rate-limit quota, circuit state, error retention, or new TTL is approved. | FR-013 |

## Dependencies and Compatibility

- **Upstream dependencies**: Public clients and the existing authentication prerequisite define the
  requests that reach the gateway; this feature does not issue tokens.
- **Downstream consumers**: All currently or later routed services rely on the gateway not changing
  their normal HTTP error contracts.
- **Compatibility promise**: Existing Product Admin status/code/message behavior and the three-field
  gateway body remain backward compatible. New gateway codes are additive only after approval.
  Downstream application status/body content remains unchanged.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Clients receive the approved stable envelope for 100% of tested gateway-owned failure
  categories without receiving internal diagnostic details.
- **SC-002**: Representative downstream 400, 404, 409, and 500 responses retain their status and body
  in 100% of gateway contract tests.
- **SC-003**: Operators can correlate 100% of tested gateway-owned failures using the approved trace
  behavior without logging or returning bearer tokens or full request bodies.

## Assumptions

- The existing `{code,message,traceId}` envelope is retained because it is already the approved
  Product Admin edge contract and adding fields is unnecessary for the stated outcome.
- The gateway remains a lean technical edge and does not gain a business domain or persistence model.
- Service-owned error standardization will be handled independently and does not block this feature.

## Human Decisions Required

| Priority | Question | Options/trade-off | Owner | Decision deadline | Resolution |
|----------|----------|-------------------|-------|-------------------|------------|
| RESOLVED | Which additional gateway failures and status policy are implemented now? | A: current three + downstream/auth unavailable as 503 + internal as 500; timeout/no-route/rate-limit/circuit behavior deferred | Gateway/Platform + security owners | Before plan approval | APPROVED 2026-07-22 |
| RESOLVED | What trace behavior applies when caller trace is absent/invalid? | A: generate a server correlation ID while preserving route rejection rules | Gateway/Platform + observability owners | Before plan approval | APPROVED 2026-07-22 |
| RESOLVED | What should unknown paths reveal? | A: preserve deny-by-default security; anonymous callers receive `UNAUTHENTICATED`, authenticated callers receive `ACCESS_DENIED` | Gateway/Platform + security owners | Before plan approval | APPROVED 2026-07-22 |

## Constitutional Constraints

- **Service ownership**: `api-gateway` owns only edge failure classification and rendering. No
  service database is accessed and no downstream business error ownership is transferred.
- **External ingress**: This feature changes failure behavior at the existing single public ingress;
  it adds no service discovery mechanism and no direct public service route.
- **API/event contracts**: A gateway-owned HTTP error contract is required before production code.
  No Kafka contract applies.
- **Durable and hot-path data**: No PostgreSQL, Redis Lua, migration, or durable state is introduced.
- **Messaging reliability**: N/A because no Kafka publication or consumption is introduced.
- **Root infrastructure ownership**: No shared Docker, Kubernetes, Helm, or monitoring asset is
  assumed. Any later operational configuration follows root `infra/` ownership rules.
- **Observability**: Existing liveness/readiness/Prometheus auto-configuration remains unchanged.
  Gateway-owned failures must follow the approved trace policy without manual registry code.
- **Verification**: Gateway unit/component tests and HTTP proxy contract tests apply. A clean
  api-gateway reactor verification is required. Database, migration, Kafka, and load tests are N/A
  because this slice adds no such behavior or scale policy.
- **Architecture decisions**: ADR 0003 remains governing; an amendment is required only if planning
  departs from its lean responsibility-oriented profile.

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-22 | Initial risk-profile draft created from the requested gateway/service error ownership split | Codex | Gateway/Platform owner | Draft |
| 2026-07-22 | Approved Options A for failure taxonomy, trace generation, and deny-by-default unknown paths | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-22 | Reconciled the approved contract with implementation and 102 passing Gateway tests | Codex | Gateway/Platform owner (user) | Verified |
