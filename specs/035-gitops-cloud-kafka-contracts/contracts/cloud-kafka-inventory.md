# Cloud Kafka Contract Inventory

All topics use three partitions and replication factor one. All subjects use
`TopicRecordNameStrategy` and `BACKWARD_TRANSITIVE`. No per-topic retention or cleanup override is
introduced.

## Topics

| Topic | Key | Records / purpose |
| --- | --- | --- |
| `campaign.lifecycle.v1` | `campaignId` | `CampaignScheduledV1`, `CampaignActivatedV1` |
| `flashsale.purchase.events.v1` | `purchaseRequestId` | `PurchaseAcceptedV1` |
| `flashsale.order.purchase-accepted.dlt.v1` | `purchaseRequestId` | Original `PurchaseAcceptedV1` recovery record |
| `flashsale.order.events.v1` | `orderId` | `OrderCreatedV1` |
| `flashsale.payment.commands.v1` | `orderId` | `PaymentRequestedV1` |
| `flashsale.payment.events.v1` | `orderId` | `PaymentSucceededV1`, `PaymentFailedV1` |
| `flashsale.payment.payment-requested.dlt.v1` | `orderId` | Original `PaymentRequestedV1` recovery record |

## Subjects

| Subject | Canonical AVSC |
| --- | --- |
| `campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/campaign.lifecycle.v1/CampaignScheduledV1.avsc` |
| `campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/campaign.lifecycle.v1/CampaignActivatedV1.avsc` |
| `flashsale.purchase.events.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc` |
| `flashsale.order.purchase-accepted.dlt.v1-com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc` |
| `flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.order.events.v1/OrderCreatedV1.avsc` |
| `flashsale.payment.commands.v1-com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.commands.v1/PaymentRequestedV1.avsc` |
| `flashsale.payment.payment-requested.dlt.v1-com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.commands.v1/PaymentRequestedV1.avsc` |
| `flashsale.payment.events.v1-com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.events.v1/PaymentSucceededV1.avsc` |
| `flashsale.payment.events.v1-com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1` | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.events.v1/PaymentFailedV1.avsc` |

## Compatibility and rollout

- Exact v1 schemas are registered before any later consumer or Payment activation.
- Compatible changes require optional/defaulted additions under the accepted subject policy.
- Rename, removal, type change, or semantic change requires a reviewed contract/version change.
- This inventory does not enable any producer, consumer, retry worker, or Payment feature flag.
