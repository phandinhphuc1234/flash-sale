# Data Model: Cart Service Scaffold

## Scope

This feature creates no business data model and no database schema.

## Ownership Established

| Concept | Owner after ADR 0002 | State in this feature |
|---------|----------------------|-----------------------|
| Pre-order cart intent | `cart-service` | Boundary only; undefined model |
| Product catalog | `product-service` | Unchanged |
| Flash-sale stock admission/reservation | `flashsale-service` | Unchanged |
| Durable order | `order-service` | Unchanged |
| Payment | `payment-service` | Unchanged |
| Identity/token | `authentication-service` | Unchanged |

## Local Database Boundary

Local PostgreSQL bootstrap provisions an empty logical database:

```text
cart_db
```

This name records database-per-service ownership only. There is no datasource configuration, table, column, constraint, index, seed record, outbox, or Liquibase changeset.

## Liquibase State

```yaml
databaseChangeLog: []
```

The `changes/` directory contains only `.gitkeep`. A later schema feature must define its entities, invariants, identifiers, relationships, indexes, lifecycle, rollback/forward-fix behavior, and PostgreSQL validation.

## Explicitly Deferred Model Decisions

- authenticated versus anonymous cart ownership;
- one cart or multiple carts per owner/channel;
- Cart and Cart Item identifiers;
- quantity limits and duplicate-item behavior;
- product, variant, price, or promotion snapshots;
- expiration and cleanup;
- merge semantics;
- availability and price revalidation;
- checkout handoff and idempotency;
- events, outbox records, and reconciliation.

None of these may be inferred from the existence of `cart_db` or empty Clean Architecture package markers.
