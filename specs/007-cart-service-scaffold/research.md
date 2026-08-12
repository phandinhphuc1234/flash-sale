# Research: Cart Service Scaffold

## Decision 1: Use a separate Cart bounded context

**Decision**: Add `cart-service` as an independently buildable business service.

**Rationale**: Mutable pre-order intent has a different lifecycle from catalog, atomic stock reservation, durable order creation, payment, and identity. The user explicitly approved the additional operational surface.

**Alternatives considered**: Client-only cart, Product-owned cart, Order-owned cart, Flash-Sale-owned cart, and deferring the boundary. Full trade-offs are recorded in ADR 0002.

## Decision 2: Use the ordinary Spring MVC business-service baseline

**Decision**: Mirror `product-service` and `order-service`, not the reactive `api-gateway` or hot-path-specific `flashsale-service`.

**Rationale**: The scaffold has no streaming or reactive requirement. Spring MVC preserves the repository's simple business-service baseline and remains compatible with the existing Java 21 virtual-thread runtime switch.

**Alternatives considered**: WebFlux was rejected because it adds a second programming model without a Cart requirement. A no-web process was rejected because future public behavior and current Actuator HTTP endpoints need the ordinary service transport baseline.

## Decision 3: Establish an empty Liquibase baseline

**Decision**: Include `liquibase-core`, an empty master changelog, and a marker-only `changes/` directory; provision `cart_db` locally but add no driver, datasource, table, or changeset.

**Rationale**: The repository migration rules establish Liquibase as the standard for every database-owning business service, and the user requested parity with existing service setup. An empty master captures ownership without inventing schema.

**Alternatives considered**: Omitting Liquibase until the first table would reduce the shell by one dependency but make Cart differ from all other database-owning business-service baselines. Creating a first Cart table was rejected because its fields and rules are not specified.

## Decision 4: Keep Cart internal and route-free

**Decision**: Add no API Gateway route and publish no base host port. Use Compose DNS `cart-service`; allow development-only host port `18089`.

**Rationale**: No Cart HTTP contract exists. The ingress rule requires future public traffic through the gateway, while a debug override can expose Actuator locally without claiming a public API.

**Alternatives considered**: Adding a placeholder gateway route was rejected because routing an unspecified endpoint creates a false contract. Publishing a host port in base Compose was rejected because it weakens the gateway-only ingress convention.

## Decision 5: Reuse declarative operational endpoints

**Decision**: Include Actuator and the runtime Prometheus registry, expose health/info/prometheus, and enable liveness/readiness probes through YAML only.

**Rationale**: This is the repository operational contract for every service. Spring Boot auto-configuration satisfies it without technology coupling in Java code.

**Alternatives considered**: Manual registry configuration was rejected by the Constitution. Adding a Cart-only container healthcheck was rejected because it would create an inconsistent Compose baseline.

## Decision 6: Update living topology, not historical features

**Decision**: Update current README, architecture, technology ownership, deployment, Compose, and Kubernetes-readiness documents. Leave features `001` through `006` unchanged.

**Rationale**: Living documents must reflect the current topology; completed feature artifacts remain historical evidence of the system at the time they were approved.

**Alternatives considered**: Replacing every old count was rejected because it corrupts Spec Kit traceability.
