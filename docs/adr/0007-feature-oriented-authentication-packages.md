# ADR 0007: Feature-oriented Authentication package layout

**Status**: Accepted  
**Date**: 2026-07-26  
**Scope**: `authentication-service` package organization only

## Context

Feature 015 currently follows a layer-first tree (`domain`, `application`, `adapter`) that preserves
Clean/Hexagonal dependencies but makes the account and session business capabilities difficult to
find. The service now contains registration, login, refresh, logout, throttling, JWT, persistence,
and cleanup responsibilities.

## Decision

Refactor the Authentication service to a feature-oriented layout while preserving the existing
Clean/Hexagonal dependency direction:

```text
account/  -> account/domain + account/application + account/adapter
session/  -> session/domain + session/application + session/adapter
security/ -> password/token/JWKS technical adapters
throttle/ -> login-throttle technical capability
cleanup/  -> retention use case and scheduler
websupport/ -> shared Auth HTTP error/trace/origin support
observability/ -> bounded metrics
configuration/ -> Spring wiring only
```

Inside each business feature the rule remains:

```text
adapter -> application -> domain
configuration -> adapter + application
```

No endpoint, database schema, error code, token claim, cookie policy, dependency, or service
boundary changes as part of this ADR. Conditional composition guards are retained so adapters are
only wired when their service-owned infrastructure is available; this affects dependency-free
bootstrap/test contexts, not a configured deployment.

## Alternatives considered

1. Keep the existing layer-first layout: lowest migration cost, but business navigation remains
   difficult as Authentication grows.
2. Put every class directly under `account` or `session`: easier to browse initially, but it would
   weaken the framework/domain boundary and encourage JPA or HTTP types to leak inward.
3. Adopt a deep per-use-case tree for every class: preserves boundaries but adds package ceremony and
   hides closely related session behavior.

The selected hybrid keeps business features visible and technical boundaries explicit.

## Consequences

Positive:

- Developers can start from the business capability (`account` or `session`).
- JPA, HTTP, Redis, JWT, and scheduler details remain isolated.
- Future features can be added locally without global DTO/mapper/exception dumping grounds.

Costs:

- Java package declarations and imports must change.
- Test package paths must mirror the new layout.
- Temporary compatibility errors are expected during the move; the module compile and tests are the
  completion gate.

## Migration rule

The refactor is mechanical. It must not change behavior and must be completed as one coherent task:
move types, update package/import references, remove obsolete empty scaffold markers, run
Authentication and Gateway tests, then record the result in Feature 015 task evidence. Conditional
Spring bean guards are allowed only to prevent adapters from loading when their required local
infrastructure is intentionally absent (for example, a dependency-free context test).
