# Service Catalog

This directory contains independently packaged Spring Boot applications. Every stateful service
owns its domain model, database schema, migrations, runtime configuration, and tests. Shared code is
restricted to technical contracts such as `common-web` and Avro records; business entities and JPA
repositories are never shared.

## Runtime services

| Service | Owns | Durable/runtime dependencies | Main boundaries |
|---|---|---|---|
| [API Gateway](api-gateway/README.md) | Public edge, routing, CORS, JWT checks, rate limiting, Swagger aggregation | Redis for distributed rate limiting | Browser/API inbound; service HTTP outbound |
| [Authentication](authentication-service/README.md) | Accounts, sessions, refresh tokens, service credentials, JWKS | PostgreSQL, Redis | Public auth HTTP; internal OAuth token HTTP |
| [Product](product-service/README.md) | Catalog, product lifecycle, authoritative product/price decisions | PostgreSQL | Public/admin HTTP; internal Product HTTP |
| [Cart](cart-service/README.md) | Shopper purchase intent and versioned Cart snapshots | PostgreSQL, Kafka consumer | Public Cart HTTP; Product HTTP; Cart reconciliation Kafka |
| [Campaign](campaign-service/README.md) | Campaign lifecycle and campaign snapshot | PostgreSQL, Kafka outbox | Admin/internal HTTP; Product/Inventory HTTP; lifecycle Kafka |
| [Flash Sale](flashsale-service/README.md) | Atomic seckill admission and reservation lifecycle | Redis, Redis Stream, PostgreSQL, Kafka | Public reservation HTTP; Campaign HTTP; Kafka commands/events |
| [Inventory](inventory-service/README.md) | Stock, movements, allocations, regular holds | PostgreSQL, Kafka | Admin/internal HTTP; regular-hold commands/events |
| [Order](order-service/README.md) | Orders and purchase Saga orchestration | PostgreSQL, Kafka | Public Order HTTP; internal HTTP clients; Saga Kafka |
| [Payment](payment-service/README.md) | Payment aggregate, Checkout, webhook receipts, provider recovery | PostgreSQL, Kafka, Stripe | Public Payment HTTP; Stripe webhook/API; Kafka |
| [Notification](notification-service/README.md) | Reserved future notification boundary | None implemented | Scaffold only |

## Dependency direction inside a service

```text
HTTP / Kafka / Scheduler adapter
             |
             v
       application input port
             |
             v
          use case
             |
             v
       domain model/rules
             |
             v
      application output port
             |
             v
JPA / Redis / Kafka / Feign adapter
```

Domain and application code must not depend on Spring MVC DTOs, JPA entities, Feign clients, Kafka
consumer records, or provider SDK types. See
[`docs/architecture/service-clean-hex-structure.md`](../docs/architecture/service-clean-hex-structure.md)
for placement rules.

## Communication policy

- External business traffic enters through API Gateway.
- Synchronous decisions that require an immediate answer use documented HTTP/OpenFeign contracts.
- Cross-service commands/facts and fan-out use versioned Kafka Avro contracts.
- Service-to-service HTTP uses short-lived client-credentials JWTs with exact subject, audience,
  token type, and scope checks.
- No service may query another service's database.
- A timeout is not automatically a business rejection; idempotent retry/recovery owns ambiguity.

## Build

From the repository root:

```powershell
.\mvnw.cmd -pl services/cart-service -am verify
.\mvnw.cmd clean verify
```

Each service README lists its public/internal boundaries, environment groups, and focused
troubleshooting steps. The canonical endpoint and payload documentation remains in
[`docs/api/`](../docs/api/README.md).
