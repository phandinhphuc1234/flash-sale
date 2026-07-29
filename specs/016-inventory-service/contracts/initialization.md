# Inventory Initialization Contract (Decision Gate)

The transport remains unresolved because Kafka decisions are intentionally deferred. Regardless of the
future transport, initialization captures a read-only SKU snapshot and never treats Inventory as the
SKU authority.

Minimum business input:

```json
{
  "eventId": "uuid",
  "variantId": "uuid",
  "sku": "SKU-SNAPSHOT"
}
```

The accepted contract must define producer ownership, topic/route, versioning, trace headers, duplicate
behavior, and SKU snapshot update semantics. Processing a duplicate must leave exactly one inventory
item with zero initial balances.
