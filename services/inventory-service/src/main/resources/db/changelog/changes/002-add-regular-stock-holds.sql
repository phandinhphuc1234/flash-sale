--liquibase formatted sql

-- Feature 049 expands Inventory without changing campaign allocation semantics. It is intentionally
-- forward-only: completed regular holds/inbox/outbox history is operational evidence and must not be
-- dropped by a code rollback.
--changeset flashsale:inventory-002-add-regular-stock-holds

CREATE TABLE regular_stock_holds (
    id UUID PRIMARY KEY,
    purchase_request_id UUID NOT NULL,
    order_id UUID NOT NULL,
    shopper_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_regular_stock_holds_purchase_request UNIQUE (purchase_request_id),
    CONSTRAINT uq_regular_stock_holds_order UNIQUE (order_id),
    CONSTRAINT ck_regular_stock_holds_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_regular_stock_holds_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_regular_stock_holds_version CHECK (version >= 0),
    CONSTRAINT ck_regular_stock_holds_exact_ttl CHECK (expires_at = created_at + INTERVAL '5 minutes'),
    CONSTRAINT ck_regular_stock_holds_terminal_timestamp CHECK (
        (status = 'HELD' AND confirmed_at IS NULL AND released_at IS NULL AND expired_at IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND released_at IS NULL AND expired_at IS NULL)
        OR (status = 'RELEASED' AND confirmed_at IS NULL AND released_at IS NOT NULL AND expired_at IS NULL)
        OR (status = 'EXPIRED' AND confirmed_at IS NULL AND released_at IS NULL AND expired_at IS NOT NULL)
    )
);

CREATE TABLE regular_stock_hold_items (
    id UUID PRIMARY KEY,
    hold_id UUID NOT NULL,
    inventory_item_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    sku_snapshot VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_regular_hold_items_hold
        FOREIGN KEY (hold_id) REFERENCES regular_stock_holds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_regular_hold_items_inventory_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE RESTRICT,
    CONSTRAINT uq_regular_hold_items_hold_variant UNIQUE (hold_id, variant_id),
    CONSTRAINT ck_regular_hold_items_quantity_positive CHECK (quantity > 0)
);

CREATE TABLE regular_hold_command_inbox (
    command_id UUID PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    hold_id UUID NOT NULL,
    purchase_request_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    result_event_id UUID NOT NULL,
    source_topic VARCHAR(200) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_regular_hold_command_inbox_source_position
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT fk_regular_hold_command_inbox_hold
        FOREIGN KEY (hold_id) REFERENCES regular_stock_holds(id) ON DELETE RESTRICT,
    CONSTRAINT uq_regular_hold_command_inbox_result_event UNIQUE (result_event_id),
    CONSTRAINT ck_regular_hold_command_inbox_type
        CHECK (command_type IN ('ConfirmRegularStockHold', 'ReleaseRegularStockHold')),
    CONSTRAINT ck_regular_hold_command_inbox_aggregate_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_regular_hold_command_inbox_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_regular_hold_command_inbox_partition CHECK (source_partition >= 0),
    CONSTRAINT ck_regular_hold_command_inbox_offset CHECK (source_offset >= 0)
);

CREATE INDEX idx_regular_stock_holds_active_expiry
    ON regular_stock_holds (status, expires_at, id) WHERE status = 'HELD';
CREATE INDEX idx_regular_hold_items_inventory_hold
    ON regular_stock_hold_items (inventory_item_id, hold_id);
CREATE INDEX idx_regular_hold_command_inbox_hold
    ON regular_hold_command_inbox (hold_id, processed_at DESC);

-- The existing campaign outbox remains readable by the current image. New envelope columns are
-- additive/defaulted so an old publisher can continue safely until the regular-hold publisher is
-- promoted. Later code writes the optional correlation, causation, trace, lease, and key fields.
ALTER TABLE outbox_events
    ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN event_key VARCHAR(64),
    ADD COLUMN correlation_id UUID,
    ADD COLUMN causation_id UUID,
    ADD COLUMN traceparent VARCHAR(256),
    ADD COLUMN tracestate VARCHAR(512),
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN claimed_by VARCHAR(200),
    ADD COLUMN claim_until TIMESTAMPTZ;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_aggregate_version_positive CHECK (aggregate_version > 0),
    ADD CONSTRAINT ck_outbox_event_version_positive CHECK (event_version > 0);

CREATE INDEX idx_outbox_events_due_lease
    ON outbox_events (status, next_attempt_at, occurred_at, id);
CREATE INDEX idx_outbox_events_claim_recovery
    ON outbox_events (claim_until) WHERE status = 'IN_PROGRESS';
