# Inventory Service schema

Liquibase owns the schema in `services/inventory-service/src/main/resources/db/changelog`.
PostgreSQL is the source of truth and the service owns all four tables:

- `inventory_items`: one row per product variant, including the captured SKU snapshot and physical/allocated quantities.
- `campaign_stock_allocations`: idempotent campaign reservations and their sold/returned terminal result.
- `stock_movements`: immutable audit ledger ordered by `created_at DESC, id DESC`.
- `outbox_events`: pending durable events; Kafka publication is intentionally deferred until the four Kafka decisions in Feature 016 are approved.

The `inventory_items.variant_id` and `campaign_stock_allocations.request_id` uniqueness constraints prevent
duplicate ownership. Allocation writes lock the inventory row pessimistically so concurrent reservations cannot
oversell the same variant.
