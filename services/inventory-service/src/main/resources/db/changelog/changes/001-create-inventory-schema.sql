--liquibase formatted sql
--changeset flashsale:inventory-001-create-schema

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    sku_snapshot VARCHAR(100) NOT NULL,
    on_hand_quantity BIGINT NOT NULL DEFAULT 0,
    campaign_allocated_quantity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inventory_variant UNIQUE (variant_id),
    CONSTRAINT chk_inventory_on_hand_non_negative CHECK (on_hand_quantity >= 0),
    CONSTRAINT chk_inventory_allocated_non_negative CHECK (campaign_allocated_quantity >= 0),
    CONSTRAINT chk_inventory_allocated_not_exceed_on_hand
        CHECK (campaign_allocated_quantity <= on_hand_quantity),
    CONSTRAINT chk_inventory_version_non_negative CHECK (version >= 0)
);

CREATE TABLE campaign_stock_allocations (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    inventory_item_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    allocated_quantity BIGINT NOT NULL,
    sold_quantity BIGINT NOT NULL DEFAULT 0,
    returned_quantity BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reconciled_at TIMESTAMPTZ,
    CONSTRAINT fk_allocation_inventory_item FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items(id),
    CONSTRAINT uk_allocation_request UNIQUE (request_id),
    CONSTRAINT uk_campaign_variant_allocation UNIQUE (campaign_id, variant_id),
    CONSTRAINT chk_allocation_quantity_positive CHECK (allocated_quantity > 0),
    CONSTRAINT chk_allocation_sold_non_negative CHECK (sold_quantity >= 0),
    CONSTRAINT chk_allocation_returned_non_negative CHECK (returned_quantity >= 0),
    CONSTRAINT chk_allocation_result_not_exceed_total
        CHECK (sold_quantity + returned_quantity <= allocated_quantity),
    CONSTRAINT chk_allocation_status CHECK (status IN ('ACTIVE', 'RELEASED', 'RECONCILED'))
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    inventory_item_id UUID NOT NULL,
    allocation_id UUID,
    reference_type VARCHAR(50),
    reference_id UUID,
    movement_type VARCHAR(40) NOT NULL,
    on_hand_delta BIGINT NOT NULL DEFAULT 0,
    allocated_delta BIGINT NOT NULL DEFAULT 0,
    on_hand_after BIGINT NOT NULL,
    allocated_after BIGINT NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_movement_inventory_item FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items(id),
    CONSTRAINT fk_movement_allocation FOREIGN KEY (allocation_id)
        REFERENCES campaign_stock_allocations(id),
    CONSTRAINT uk_stock_movement_request UNIQUE (request_id),
    CONSTRAINT chk_movement_has_change CHECK (on_hand_delta <> 0 OR allocated_delta <> 0),
    CONSTRAINT chk_movement_on_hand_after CHECK (on_hand_after >= 0),
    CONSTRAINT chk_movement_allocated_after CHECK (allocated_after >= 0)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_stock_movements_inventory_created
    ON stock_movements (inventory_item_id, created_at DESC, id DESC);
CREATE INDEX idx_stock_movements_reference
    ON stock_movements (reference_type, reference_id);
CREATE INDEX idx_outbox_pending
    ON outbox_events (status, occurred_at, id) WHERE status = 'PENDING';

--rollback DROP TABLE outbox_events;
--rollback DROP TABLE stock_movements;
--rollback DROP TABLE campaign_stock_allocations;
--rollback DROP TABLE inventory_items;
