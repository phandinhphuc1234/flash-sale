# Phase 1 Data Model: Campaign Management MVP

**Feature**: `017-campaign-management-mvp`  
**Date**: 2026-07-30  
**Durable truth**: PostgreSQL, database-per-service ownership

## Ownership map

| Schema | Owner | Feature 017 changes |
|---|---|---|
| `campaign_db` | Campaign Service | Campaign, item, schedule operation, outbox tables |
| `auth_db` | Authentication Service | Durable OAuth service clients and allowed scopes |
| `product_db` | Product Service | No schema change; read existing Product/Variant truth |
| `inventory_db` | Inventory Service | No schema change; use existing campaign allocation |

No service reads or joins another service's schema.

## Aggregate model

```text
Campaign (aggregate root)
  1 ── 0..1 CampaignItem

Campaign
  1 ── * CampaignScheduleOperation

Campaign
  1 ── * CampaignOutboxEvent
```

`CampaignItem` is represented in a separate table for future evolution, but a unique campaign key
enforces the approved one-item MVP.

## 1. `campaigns`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | no | primary key, application-generated |
| `code` | VARCHAR(64) | no | stored uppercase; unique without case-only duplicates |
| `name` | VARCHAR(200) | no | non-blank application validation |
| `status` | VARCHAR(32) | no | `DRAFT`, `SCHEDULED`, `ACTIVE`, `ENDED` |
| `start_at` | TIMESTAMPTZ | no | strictly before `end_at` |
| `end_at` | TIMESTAMPTZ | no | strictly after `start_at` |
| `scheduled_at` | TIMESTAMPTZ | yes | set on successful schedule |
| `activated_at` | TIMESTAMPTZ | yes | set on successful activation |
| `ended_at` | TIMESTAMPTZ | yes | set on successful ending |
| `version` | BIGINT | no | JPA optimistic version; starts at zero |
| `created_by` | VARCHAR(100) | no | initiating administrator subject |
| `updated_by` | VARCHAR(100) | no | last initiating administrator/system actor |
| `created_at` | TIMESTAMPTZ | no | UTC audit time |
| `updated_at` | TIMESTAMPTZ | no | UTC audit time |

Constraints/indexes:

- primary key `id`;
- unique index on `UPPER(code)` plus application normalization;
- check `code = UPPER(code)`;
- check `start_at < end_at`;
- check status set;
- index `(status, start_at, id)` for activation scans;
- index `(status, end_at, id)` for ending scans.

State rules:

```text
DRAFT -> SCHEDULED -> ACTIVE -> ENDED
```

Only DRAFT metadata/item fields are mutable. Lifecycle workers update status/timestamps/version via
conditional statements that include current status, expected version, and time boundary.

## 2. `campaign_items`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | no | primary key |
| `campaign_id` | UUID | no | FK to `campaigns`; unique for one-item MVP |
| `product_id` | UUID | yes | Product snapshot; required after schedule |
| `variant_id` | UUID | no | configured Product variant |
| `inventory_allocation_id` | UUID | yes | required and unique after schedule |
| `variant_sku_snapshot` | VARCHAR(100) | yes | required after schedule |
| `base_price_snapshot` | NUMERIC(19,4) | yes | positive and required after schedule |
| `currency_snapshot` | CHAR(3) | yes | approved initial currency `VND` |
| `campaign_price` | NUMERIC(19,4) | no | positive; lower than base price at schedule |
| `requested_quantity` | BIGINT | no | positive |
| `allocated_quantity` | BIGINT | no | non-negative; equals requested after schedule |
| `purchase_limit_per_user` | BIGINT | no | positive and not greater than requested/allocated |
| `created_at` | TIMESTAMPTZ | no | UTC audit time |
| `updated_at` | TIMESTAMPTZ | no | UTC audit time |

Constraints/indexes:

- FK `campaign_id` -> `campaigns(id)`;
- unique `campaign_id`;
- unique `(campaign_id, variant_id)`;
- partial unique `inventory_allocation_id WHERE inventory_allocation_id IS NOT NULL`;
- checks for positive price/requested quantity/purchase limit and non-negative allocated quantity;
- check nullable currency is `VND`;
- index `variant_id`.

Database checks protect scalar integrity. Cross-field lifecycle invariants that depend on Campaign
status and Product/Inventory snapshots remain in the domain/application transaction.

## 3. `campaign_schedule_operations`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | no | operation identity |
| `campaign_id` | UUID | no | FK to Campaign |
| `idempotency_key` | VARCHAR(128) | no | indefinitely retained, never reused for a new command |
| `inventory_request_id` | UUID | no | stable across every retry/recovery |
| `request_hash` | CHAR(64) | no | SHA-256 of canonical command identity/configuration |
| `campaign_version` | BIGINT | no | expected version captured when operation begins |
| `operation_status` | VARCHAR(32) | no | `STARTED`, `INVENTORY_ALLOCATED`, `COMPLETED`, `FAILED` |
| `attempt_count` | INTEGER | no | non-negative execution attempts |
| `last_failure_code` | VARCHAR(100) | yes | stable client/operational code |
| `last_failure_message` | VARCHAR(1000) | yes | sanitized, no token/secret |
| `initiated_by` | VARCHAR(100) | no | original administrator subject |
| `caller_service` | VARCHAR(100) | yes | `campaign-service` for background resume |
| `trace_id` | VARCHAR(128) | no | initiating/current correlation identity policy |
| `created_at` | TIMESTAMPTZ | no | UTC |
| `updated_at` | TIMESTAMPTZ | no | UTC |

Constraints/indexes:

- FK `campaign_id` -> `campaigns(id)`;
- unique `(campaign_id, idempotency_key)`;
- unique `inventory_request_id`;
- partial unique `campaign_id` for statuses `STARTED`, `INVENTORY_ALLOCATED`;
- status check and non-negative attempt count;
- index `(operation_status, updated_at, id)` for recovery scans.

Transition rules:

```text
STARTED -> INVENTORY_ALLOCATED -> COMPLETED
STARTED -> FAILED                 (known Product/Inventory business rejection)
INVENTORY_ALLOCATED -> COMPLETED  (retry/recovery)
FAILED -> STARTED                 (same key/hash/configuration explicitly retried)
```

Reopening `FAILED` is allowed only for the same retained idempotency key, request hash, Campaign
version/configuration, and Inventory request ID; it increments the attempt count. This permits a
previously ineligible Product or insufficient Inventory condition to be retried after its owning
service changes, without letting the key represent a new command. An ambiguous timeout does not
replace the operation or Inventory request ID. Recalling Inventory with the same request ID resolves
whether allocation already occurred.

## 4. `campaign_outbox_events`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | no | stable `eventId` and primary key |
| `aggregate_id` | UUID | no | Campaign ID |
| `aggregate_version` | BIGINT | no | version after transition |
| `event_type` | VARCHAR(100) | no | `CampaignScheduled` or `CampaignActivated` |
| `event_version` | INTEGER | no | `1` |
| `event_key` | VARCHAR(100) | no | Campaign ID text used as Kafka key |
| `payload` | JSONB | no | immutable internal source payload mapped to the approved Avro SpecificRecord by the relay |
| `publish_status` | VARCHAR(32) | no | `PENDING`, `PROCESSING`, `PUBLISHED`, `FAILED` |
| `retry_count` | INTEGER | no | current automatic attempt count |
| `next_attempt_at` | TIMESTAMPTZ | yes | due time for PENDING retry |
| `claimed_by` | VARCHAR(100) | yes | publisher instance identity |
| `claimed_until` | TIMESTAMPTZ | yes | crash-recovery lease |
| `occurred_at` | TIMESTAMPTZ | no | business transition time |
| `published_at` | TIMESTAMPTZ | yes | successful acknowledgement time |
| `last_error` | VARCHAR(2000) | yes | sanitized failure text |
| `requeue_count` | INTEGER | no | explicit operator recoveries |
| `requeued_by` | VARCHAR(100) | yes | authorized operator subject |
| `requeued_at` | TIMESTAMPTZ | yes | latest explicit recovery time |
| `trace_id` | VARCHAR(128) | no | internal transition correlation metadata used to build W3C Kafka headers; never serialized into the Avro body |
| `created_at` | TIMESTAMPTZ | no | UTC |
| `updated_at` | TIMESTAMPTZ | no | UTC |

Constraints/indexes:

- unique `(aggregate_id, aggregate_version, event_type)`;
- check status, positive event version, non-negative retry/requeue counts;
- due index `(publish_status, next_attempt_at, created_at, id)`;
- aggregate-order index `(aggregate_id, aggregate_version, id)`;
- lease-recovery index `(publish_status, claimed_until)`.

Claim/order rule: a publisher may claim only the earliest non-published event for an aggregate. A
terminally failed predecessor blocks later aggregate events until the same row is requeued. Explicit
requeue preserves `id`, payload, aggregate version, event type, and event version; it resets the
automatic retry counter and records operator/requeue audit.

## 5. Authentication-owned `oauth_clients`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | no | primary key |
| `client_id` | VARCHAR(100) | no | unique service identity |
| `client_secret_hash` | VARCHAR(255) | no | encoded; plaintext prohibited |
| `grant_type` | VARCHAR(64) | no | only `client_credentials` |
| `status` | VARCHAR(32) | no | `ACTIVE`, `INACTIVE` |
| `access_token_ttl_seconds` | INTEGER | no | positive and at most 300 for approved clients |
| `created_at` | TIMESTAMPTZ | no | UTC |
| `updated_at` | TIMESTAMPTZ | no | UTC |

Initial fixed registrations:

| `client_id` | TTL | Purpose |
|---|---:|---|
| `campaign-service` | 300 seconds | Product validation and Inventory campaign allocation |
| `flashsale-service` | 300 seconds | Campaign snapshot recovery/cache rebuilding |

## 6. Authentication-owned `oauth_client_scopes`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `oauth_client_id` | UUID | no | FK to `oauth_clients(id)` |
| `scope` | VARCHAR(128) | no | least-privilege allowed scope |

Primary key: `(oauth_client_id, scope)`.

Initial scope rows:

```text
campaign-service -> catalog.read
campaign-service -> inventory.campaign.allocate
campaign-service -> inventory.campaign.release  # registered for future feature, not requested here
flashsale-service -> campaign.snapshot.read
```

No access token or raw client secret is stored in Campaign tables. Authentication may keep
short-lived authorization-server token state in memory for this MVP; JWT validation remains local at
resource services.

## 7. Transport snapshots (not shared entities)

### Product validation result

```text
productId, variantId, sku, productStatus, variantStatus, sellable, basePrice, currency
```

### Inventory allocation result mapping

```text
Inventory data.id          -> Campaign inventoryAllocationId
Inventory data.requestId   -> stable inventoryRequestId verification
Inventory allocatedQuantity -> Campaign allocatedQuantity
Inventory status           -> must represent complete active allocation
```

These are adapter DTOs/results. They are not shared Product/Inventory domain types.

## 8. Migration and rollback strategy

- Campaign creates one initial SQL-formatted Liquibase changeset containing its four tables and
  indexes, included by Campaign's YAML master changelog.
- Authentication adds a separate changeset for the two client registry tables; no plaintext client
  data is embedded in migration files.
- Product, Inventory, Gateway, and Flash Sale require no schema migration for Feature 017.
- Rollback drops children before parents: outbox/operations/items then campaigns; scopes then clients.
- Migrations run through each owning service module/Testcontainers and through the repository's
  one-off local migration workflow; Hibernate remains `ddl-auto=validate`.
