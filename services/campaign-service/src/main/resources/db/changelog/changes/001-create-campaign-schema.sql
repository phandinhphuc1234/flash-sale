--liquibase formatted sql
--changeset flashsale:campaign-001-create-schema

-- Campaign aggregate root and lifecycle state.
CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    scheduled_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_campaigns_code_upper CHECK (code = UPPER(code)),
    CONSTRAINT ck_campaigns_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED')),
    CONSTRAINT ck_campaigns_time_window CHECK (start_at < end_at),
    CONSTRAINT ck_campaigns_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_campaigns_code_upper ON campaigns (UPPER(code));
CREATE INDEX idx_campaigns_status_start ON campaigns (status, start_at, id);
CREATE INDEX idx_campaigns_status_end ON campaigns (status, end_at, id);

-- The single sellable product variant configured for a Campaign in the MVP.
CREATE TABLE campaign_items (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL,
    product_id UUID,
    variant_id UUID NOT NULL,
    inventory_allocation_id UUID,
    variant_sku_snapshot VARCHAR(100),
    base_price_snapshot NUMERIC(19, 4),
    currency_snapshot CHAR(3),
    campaign_price NUMERIC(19, 4) NOT NULL,
    requested_quantity BIGINT NOT NULL,
    allocated_quantity BIGINT NOT NULL DEFAULT 0,
    purchase_limit_per_user BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_campaign_items_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT uq_campaign_items_campaign UNIQUE (campaign_id),
    CONSTRAINT uq_campaign_items_campaign_variant UNIQUE (campaign_id, variant_id),
    CONSTRAINT ck_campaign_items_currency
        CHECK (currency_snapshot IS NULL OR currency_snapshot = 'VND'),
    CONSTRAINT ck_campaign_items_campaign_price CHECK (campaign_price > 0),
    CONSTRAINT ck_campaign_items_requested_quantity CHECK (requested_quantity > 0),
    CONSTRAINT ck_campaign_items_allocated_quantity CHECK (allocated_quantity >= 0),
    CONSTRAINT ck_campaign_items_purchase_limit CHECK (
        purchase_limit_per_user > 0 AND purchase_limit_per_user <= requested_quantity
    ),
    CONSTRAINT ck_campaign_items_base_price CHECK (
        base_price_snapshot IS NULL OR base_price_snapshot > 0
    )
);

CREATE INDEX idx_campaign_items_variant ON campaign_items (variant_id);
CREATE UNIQUE INDEX uq_campaign_items_inventory_allocation_present
    ON campaign_items (inventory_allocation_id)
    WHERE inventory_allocation_id IS NOT NULL;

-- Durable idempotency and recovery state for a Campaign scheduling attempt.
CREATE TABLE campaign_schedule_operations (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    inventory_request_id UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    campaign_version BIGINT NOT NULL,
    operation_status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_failure_code VARCHAR(100),
    last_failure_message VARCHAR(1000),
    initiated_by VARCHAR(100) NOT NULL,
    caller_service VARCHAR(100),
    trace_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_campaign_schedule_operations_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT uq_campaign_schedule_operations_key
        UNIQUE (campaign_id, idempotency_key),
    CONSTRAINT uq_campaign_schedule_operations_inventory_request
        UNIQUE (inventory_request_id),
    CONSTRAINT ck_campaign_schedule_operations_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_campaign_schedule_operations_version CHECK (campaign_version >= 0),
    CONSTRAINT ck_campaign_schedule_operations_status CHECK (
        operation_status IN ('STARTED', 'INVENTORY_ALLOCATED', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_campaign_schedule_operations_attempt_count CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX uq_campaign_schedule_operations_active_campaign
    ON campaign_schedule_operations (campaign_id)
    WHERE operation_status IN ('STARTED', 'INVENTORY_ALLOCATED');
CREATE INDEX idx_campaign_schedule_operations_recovery
    ON campaign_schedule_operations (operation_status, updated_at, id);

-- Transactional outbox records for versioned Campaign lifecycle events.
CREATE TABLE campaign_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    publish_status VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    claimed_by VARCHAR(100),
    claimed_until TIMESTAMPTZ,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    requeue_count INTEGER NOT NULL DEFAULT 0,
    requeued_by VARCHAR(100),
    requeued_at TIMESTAMPTZ,
    trace_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_campaign_outbox_events_campaign
        FOREIGN KEY (aggregate_id) REFERENCES campaigns (id),
    CONSTRAINT uq_campaign_outbox_events_aggregate_event
        UNIQUE (aggregate_id, aggregate_version, event_type),
    CONSTRAINT ck_campaign_outbox_events_aggregate_version CHECK (aggregate_version >= 0),
    CONSTRAINT ck_campaign_outbox_events_type
        CHECK (event_type IN ('CampaignScheduled', 'CampaignActivated')),
    CONSTRAINT ck_campaign_outbox_events_version CHECK (event_version > 0),
    CONSTRAINT ck_campaign_outbox_events_status CHECK (
        publish_status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_campaign_outbox_events_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_campaign_outbox_events_requeue_count CHECK (requeue_count >= 0)
);

CREATE INDEX idx_campaign_outbox_events_due
    ON campaign_outbox_events (publish_status, next_attempt_at, created_at, id);
CREATE INDEX idx_campaign_outbox_events_aggregate_order
    ON campaign_outbox_events (aggregate_id, aggregate_version, id);
CREATE INDEX idx_campaign_outbox_events_lease_recovery
    ON campaign_outbox_events (publish_status, claimed_until);

--rollback DROP TABLE campaign_outbox_events;
--rollback DROP TABLE campaign_schedule_operations;
--rollback DROP TABLE campaign_items;
--rollback DROP TABLE campaigns;
