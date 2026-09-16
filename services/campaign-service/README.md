# Campaign Service

Campaign Service owns campaign lifecycle, the frozen sale snapshot, and preparation orchestration.
It validates Product and allocates Inventory synchronously before publishing lifecycle facts.

## Lifecycle

```text
DRAFT -> SCHEDULED -> ACTIVE
```

Administrator operations create/replace the draft and item snapshot, schedule it with an
idempotency key, activate it, and requeue an owned terminal outbox event. Scheduling calls Product
for the variant snapshot and Inventory for an idempotent campaign allocation before the final local
transaction commits `CampaignScheduledV1` to the outbox.

## Boundaries

- public admin: `/api/v1/admin/campaigns/**`;
- Flash Sale recovery: `GET /internal/v1/campaigns/{campaignId}/snapshot`;
- outbound HTTP: Product campaign validation and Inventory allocation;
- outbound Kafka: `CampaignScheduledV1` and `CampaignActivatedV1` on `campaign.lifecycle.v1`.

Campaign end/cancellation message families described as candidates in architecture documentation
are not claimed as implemented.

## Reliability and data

Campaign owns `campaign_db`, its Liquibase changes, and a transactional outbox. Claim leases,
bounded retries, terminal failure state, and the admin requeue boundary prevent silent publication
loss. HTTP client timeouts are explicit and service credentials are acquired through Authentication.

## Configuration and verification

Important groups are datasource, JWT/internal audience, OAuth client credentials, Product/Inventory
URLs and timeouts, Kafka/Schema Registry, lifecycle topic, and outbox/lifecycle scheduler settings.

```powershell
.\mvnw.cmd -pl services/campaign-service -am verify
```

For exact request bodies and status codes, use [`docs/api/README.md`](../../docs/api/README.md).

## Troubleshooting

- Schedule fails: separate Product validation, Inventory allocation, and local transaction errors.
- Flash Sale lacks a campaign: inspect outbox state and `campaign.lifecycle.v1`, then use the internal
  snapshot recovery boundary rather than editing Flash Sale storage.
- Requeue only an event owned by this Campaign and only after the root cause is corrected.
