# ADR 0002: Cart Service Boundary

**Status**: Accepted

**Date**: 2026-07-15

## Context

The current topology separates authoritative catalog data, flash-sale stock admission, durable orders, payments, and identity. A shopping cart represents mutable pre-order intent: it can change repeatedly before any order exists and can evolve, scale, or expire on a lifecycle different from those contexts.

Placing Cart in an existing service would make that service own unrelated state. `product-service` would mix customer intent with catalog ownership; `flashsale-service` would mix a normal browsing workflow with the narrow atomic hot path; and `order-service` would treat an uncommitted selection as a durable order. The user has explicitly selected an independent Cart boundary despite the cost of another deployable service.

The first change is intentionally only a service shell. Policies such as authenticated or anonymous carts, merge behavior, item limits, snapshots, price revalidation, availability checks, expiration, checkout coordination, and event publication are not yet specified.

## Decision

Add an independently buildable and deployable `cart-service` with Java package ownership under `com.philia.flashsale.cart`.

Its future bounded context is pre-order cart intent and cart lifecycle. It does not own:

- product catalog or authoritative product data;
- authoritative pricing or promotion decisions;
- stock counters, flash-sale admission, or reservation;
- durable orders or checkout completion;
- payments; or
- user identity and token issuance.

The scaffold follows the standard business-service Clean/Hexagonal package shape and dependency direction:

```text
adapter -> application -> domain
configuration -> adapter + application
```

The service owns its runtime configuration, image recipe, tests, and future migrations. Root `infra/` owns shared Compose topology, local PostgreSQL database provisioning, and future Kubernetes resources. A local logical `cart_db` is provisioned as the future database ownership boundary, but this scaffold adds no table or business migration.

The shell exposes the repository's declarative Actuator health, liveness, readiness, info, and Prometheus endpoints. Spring Boot auto-configures the registry; production code does not construct it manually.

This decision does not approve a Cart API, API Gateway route, entity, repository, Redis integration, Kafka contract, event producer/consumer, or inter-service client. Each future interaction requires a separate approved specification, documented HTTP or versioned Kafka contract, and an implementation plan.

## Consequences

- The monorepo grows from nine to ten service modules and from eight to nine business services.
- Cart receives an explicit ownership, build, test, image, configuration, metrics, database, and future deployment surface.
- Cart can evolve and scale independently from Product, Flash Sale, Order, and Payment.
- The system gains another process to build, deploy, secure, observe, operate, and keep compatible.
- Future features must resolve consistency and user-experience questions at Cart boundaries without reading another service's database.
- A shell with no business endpoint is intentionally not useful to clients until a later feature is specified.

## Migration Impact

- Add `services/cart-service` to the Maven reactor and current local Compose `apps` topology.
- Provision a new empty logical database named `cart_db` for a fresh local PostgreSQL volume.
- Add development-only host port `18089` without changing existing port assignments.
- Update living repository, architecture, technology-ownership, Compose, and Kubernetes-readiness documentation.
- No existing API, event, table, record, or user data is migrated.
- PostgreSQL initialization scripts run only for a new volume; existing environments must create `cart_db` explicitly and non-destructively when they choose to run Cart.
- Before Cart contains business data or contracts, rollback is removal of the module, Compose entry, empty database provisioning, and living-document references. Once real state exists, a later ADR or migration plan must define a data-safe rollback.

## Alternatives considered

### Keep the cart only in Web/Mobile clients

Rejected because the requested system needs an independently owned server-side Cart boundary. Client-only state can still be evaluated later as a UX cache, but it cannot replace the approved service boundary.

### Put Cart in product-service

Rejected because catalog ownership and customer-specific mutable intent have different responsibilities, access patterns, and lifecycle. Product must not become the owner of carts.

### Put Cart in order-service

Rejected because a cart is not yet a durable order. Combining them would blur pre-order intent with committed order lifecycle and make independent evolution harder.

### Put Cart in flashsale-service

Rejected because Flash Sale owns the narrow atomic reservation/admission hot path. A general cart should not expand or slow that boundary.

### Defer the boundary until the first Cart business feature

Rejected because the user explicitly requested the independent service setup now. The risk of speculative behavior is controlled by keeping the module marker-only and deferring every business rule, schema, route, and contract.
