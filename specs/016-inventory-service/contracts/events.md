# Inventory Kafka Contracts (Draft)

Event topic names, keys, headers, schema versions, retry policy, and initialization trigger are unresolved
planning decisions. The payload shapes below are minimum business data from the source specification.

## Required success events

### `CampaignStockAllocated.v1`

```json
{
  "eventId": "uuid",
  "eventType": "CampaignStockAllocated.v1",
  "occurredAt": "2026-07-27T09:00:00Z",
  "allocationId": "uuid",
  "campaignId": "uuid",
  "variantId": "uuid",
  "allocatedQuantity": 100
}
```

### `CampaignStockReleased.v1`

Contains `eventId`, `eventType`, `occurredAt`, `allocationId`, `campaignId`, `variantId`, and
`releasedQuantity`.

### `CampaignStockReconciled.v1`

Contains `eventId`, `eventType`, `occurredAt`, `allocationId`, `campaignId`, `variantId`,
`allocatedQuantity`, `soldQuantity`, and `returnedQuantity`.

## Conditional events

`InventoryAdjusted.v1` and `CampaignStockAllocationRejected.v1` require explicit approval. A rejected
allocation cannot be written into the same transaction that rolls back the rejection, so the rejection
delivery strategy must be selected before implementation.

## Delivery rules

- Success events are inserted as `PENDING` outbox rows in the command transaction.
- A publisher sends at least once and marks rows `PUBLISHED` or `FAILED` according to the approved policy.
- Event ID and aggregate ID are stable; trace/correlation headers propagate from the originating command.
- Consumers, especially `flashsale-service`, must tolerate duplicate delivery.
