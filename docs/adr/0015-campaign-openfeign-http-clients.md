# ADR 0015: OpenFeign for Campaign Internal HTTP Clients

- **Status**: Accepted
- **Date**: 2026-08-03
- **Decision owner**: Project owner
- **Feature**: `017-campaign-management-mvp`
- **Related**: ADR 0012 (Campaign OAuth2 Client Credentials), ADR 0013 (Campaign Snapshot Identity)

## Context

Campaign must call Product and Inventory synchronously while scheduling a campaign. The calls are
blocking request/response interactions and must preserve documented HTTP contracts, endpoint-specific
service scopes, `X-Trace-Id`, bounded timeouts, stable error mapping, and Inventory request identity.

The application layer must remain independent from the HTTP client implementation.

## Decision

Campaign will use Spring Cloud OpenFeign for its Product and Inventory outbound HTTP adapters.

Each Feign interface is transport-only and lives under the owning feature's
`adapter/out/client/{product,inventory}` package. An adapter wraps the interface and translates
wire DTOs and transport failures into the application's output ports and results. Feign types must
not appear in domain models, application ports, or use cases.

The Campaign service adds `org.springframework.cloud:spring-cloud-starter-openfeign`. The existing
OAuth2 Client Credentials manager remains responsible for obtaining the correct scope-specific
service token. Feign request configuration attaches the selected bearer token, propagates
`X-Trace-Id`, and applies bounded connect/read timeouts. Product uses a 500-ms connect timeout and
1,200-ms read timeout. Inventory uses a 500-ms connect timeout and 1,000-ms read timeout so the
combined call remains inside the approved 2,500-ms Campaign budget. Automatic Feign retry is
disabled. The adapter owns HTTP status/error
translation; business retries and Inventory idempotency remain governed by the approved Feature 017
contracts.

This decision changes only the outbound HTTP client implementation. It does not change service
boundaries, HTTP contracts, JWT claims, Kafka topics, API Gateway routing, or application/domain
logic.

## Alternatives considered

### Spring `RestClient`

Rejected for Feature 017 because two typed internal clients would require repetitive request
construction while OpenFeign provides the same blocking adapter boundary with declarative contracts.

### Spring HTTP Interface Client

Deferred. It is a valid future option, but Feature 017 standardizes on OpenFeign for the current
Campaign integration slice.

### WebClient or gRPC

Rejected for this feature because Campaign is servlet-based and the approved contracts are HTTP;
reactive or protobuf transport is outside this scope.

## Consequences

Positive:

- concise typed declarations for Product and Inventory calls;
- application/domain boundaries remain unchanged;
- OAuth2, tracing, timeout, error, and idempotency policies remain explicit in adapters/configuration.

Costs:

- Campaign gains a Spring Cloud OpenFeign dependency and generated client proxies;
- Feign configuration and contract tests must prevent token/scope leakage between clients;
- the plan and dependency approval must be re-approved before implementation.

## Rollout and verification

Update Campaign's dependency/configuration tasks first, then implement T053–T055. Verify Product and
Inventory status/error mapping, scope-specific token attachment, trace propagation, timeout behavior,
and no administrator-token relay through contract and integration tests.

## Approval

Accepted by the project owner on 2026-08-03 through the explicit request to implement Feature 017
tasks T053–T055 with the repository OpenFeign skill.
