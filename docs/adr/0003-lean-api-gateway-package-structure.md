# ADR 0003: Lean API Gateway Package Structure

**Status**: Accepted

**Date**: 2026-07-20

## Context

`api-gateway` is the public reactive edge of the system. Its current responsibilities are route
forwarding, request-boundary filters, JWT authorization, stable HTTP error translation, and edge
observability. It does not own a business Aggregate, application use case, durable state, or an
outbound domain capability.

The original scaffold reserved `domain`, `application`, and `adapter` packages so every module had
a recognizable Clean/Hexagonal shape. Once real gateway code was added, that generic business-
service scaffold coexisted with a technical edge structure such as `filter`, `security`, and
`error`. The two shapes made ownership harder to read and left empty packages that implied business
responsibilities the gateway does not have.

## Decision

Use a lean, responsibility-oriented package structure for `api-gateway`:

```text
com/philia/flashsale/gateway/
├── ApiGatewayApplication.java
├── configuration/       # General WebFlux/client/runtime wiring, created when needed
├── routing/             # Java route definitions when routes are not declarative YAML
├── filter/
│   ├── global/          # Filters applied across matching gateway traffic
│   └── route/           # Filters attached to selected routes
├── security/            # Reactive security rules, JWT conversion, and security failures
├── error/               # Gateway-owned HTTP error representation and serialization
├── faulttolerance/      # Circuit-breaker/fallback code when an approved feature needs it
├── ratelimit/           # Rate-limit keys and policy integration when approved
└── observability/       # Edge tracing/metrics code beyond declarative Actuator configuration
```

The existing gateway classes move by their actual responsibility:

- `AdminCatalogRequestBoundaryFilter` moves to `filter/global`.
- `GatewayAdminErrorResponse` and `GatewayHttpErrorWriter` move to `error`.
- `GatewaySecurityErrorHandler` and `GatewaySecurityConfiguration` move to `security`.

The empty gateway `domain`, `application`, and `adapter` scaffold is removed. These package names
are not prohibited forever, but they may be introduced only if a later approved feature gives the
gateway a genuine business policy, use case, or outbound capability that cannot remain in an edge
package. Such a change must update the plan and architecture decision as applicable.

The gateway remains subject to Clean Architecture at the responsibility level: routing, reactive
HTTP, Spring Security, and provider types stay at the edge and do not move into business services.
Business workflows and domain invariants remain in the service that owns them. Package uniformity
with business services is not a goal.

Routes remain declarative in `application.yml` unless an approved feature needs Java route
definitions. Marker-only technical directories reserve the user-approved edge structure; a marker
is removed as soon as a real class occupies its directory.

## Consequences

- The gateway tree reflects responsibilities that exist or are explicitly reserved for the edge.
- Security configuration is discoverable under `security` instead of being split between a generic
  configuration package and security error classes elsewhere.
- Filters, error serialization, and security failures are separated without introducing business
  ports, domain models, or persistence abstractions.
- Gateway code is intentionally organized differently from business services that use full
  Domain/Application/Adapter boundaries.
- A later business responsibility added to the gateway requires explicit justification; the empty
  Clean/Hexagonal scaffold is not recreated speculatively.
- Package moves require a clean Maven build so stale bytecode from the old packages cannot be
  discovered as duplicate Spring components.

## Migration Impact

- Move five Java classes and update their package declarations/imports.
- Update the affected gateway test import without changing assertions or HTTP behavior.
- Remove redundant markers and the empty legacy `domain`, `application`, and `adapter` scaffold.
- Keep the approved technical edge markers that do not yet have a concrete class.
- Update the living architecture guide and active Feature 010 implementation plan/task ledger.
- No route, JWT claim, authorization rule, HTTP status/body, dependency, deployment, or public
  contract changes.

## Implementation follow-up

On 2026-07-22, the gateway-local `GatewayAdminErrorResponse` type was renamed to
`GatewayErrorResponse` because the `error` package owns every failure produced by the gateway edge,
not Product Admin business failures. The body remains `{code,message,traceId}` and the three
Feature 010 codes remain unchanged. Empty Java placeholders for unimplemented rate limiting,
circuit breaking, global exception handling, and observability were removed; the accepted
technical package reservations remain marker-only until an approved feature introduces behavior.

## Alternatives considered

### Keep the full business-service Clean/Hexagonal scaffold

Rejected because the gateway currently owns no business core or application use case. Empty ports,
use cases, and domain packages communicate responsibilities that do not exist.

### Keep both the old scaffold and the new technical packages

Rejected because two simultaneous organization schemes make class placement ambiguous and leave
duplicate conceptual homes for filters, security, and error translation.

### Put every gateway class in one `configuration` package

Rejected because filters, HTTP error serialization, security behavior, rate limiting, routing, and
observability are distinct edge responsibilities even though all of them use framework types.
