# Kafka Avro Contracts

This Maven module is the executable schema authority for implemented Kafka records. The Avro Maven
plugin generates Java `SpecificRecord` classes during the build; producers and consumers depend on
this module instead of duplicating payload classes.

## Implemented topic families

| Topic | Records |
|---|---|
| `campaign.lifecycle.v1` | `CampaignScheduledV1`, `CampaignActivatedV1` |
| `flashsale.purchase.events.v1` | accepted, reservation confirmed, reservation released |
| `flashsale.purchase.commands.v1` | confirm/release flash-sale reservation |
| `flashsale.payment.commands.v1` | payment requested |
| `flashsale.payment.events.v1` | payment succeeded/failed |
| `flashsale.order.events.v1` | Order lifecycle V1 and compatible V2 records |
| `flashsale.inventory.regular-hold.commands.v1` | confirm/release regular hold |
| `flashsale.inventory.regular-hold.events.v1` | regular hold confirmed/released/expired |
| `flashsale.cart.checkout.commands.v1` | reconcile purchased Cart snapshot |

Schemas live under `src/main/avro/topics/<topic>/`. The filename and record namespace are versioned;
topic routing and Schema Registry subject policy remain deployment concerns.

## Build and compatibility

```powershell
.\mvnw.cmd -pl contracts/kafka-avro-contracts -am clean verify
```

Tests cover schema parsing, logical types/defaults, historical fixtures, and the compatibility rules
required by owning features. Cloud provisioning uses `TopicRecordNameStrategy` and controlled
`BACKWARD_TRANSITIVE` subjects; see the Phase 20 GitOps helper before changing a subject inventory.

## Evolution checklist

1. Approve the semantic change in the producer-owned feature contract.
2. Add a new record version or a backward-compatible field with a safe default.
3. Preserve identities, money precision, UTC timestamps, and correlation/causation semantics.
4. Add compatibility fixtures/tests before changing producer or consumer code.
5. Update topic/subject provisioning and the Kafka catalog.
6. Deploy consumers that can read the new record before producers emit it when rollout order
   requires it.

Do not delete or rewrite a registered schema to make a test pass. Correct the evolution or create an
explicit new version.
