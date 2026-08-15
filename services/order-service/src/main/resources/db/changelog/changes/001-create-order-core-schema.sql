--liquibase formatted sql

-- Order Service owns these durable tables. No foreign key crosses a service boundary.
--changeset order:001-create-order-core-schema

CREATE TABLE orders (
    id UUID NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    purchase_request_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal_amount NUMERIC(19,4) NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT uq_orders_purchase_request UNIQUE (purchase_request_id),
    CONSTRAINT uq_orders_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_orders_order_number_non_blank CHECK (btrim(order_number) <> ''),
    CONSTRAINT ck_orders_status CHECK (status = 'PENDING_PAYMENT'),
    CONSTRAINT ck_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_orders_subtotal_positive CHECK (subtotal_amount > 0),
    CONSTRAINT ck_orders_total_positive CHECK (total_amount > 0),
    CONSTRAINT ck_orders_total_equals_subtotal CHECK (total_amount = subtotal_amount),
    CONSTRAINT ck_orders_reservation_window CHECK (reservation_expires_at > accepted_at)
);

CREATE INDEX idx_orders_owner_created
    ON orders (user_id, created_at DESC, id DESC);

CREATE TABLE order_lines (
    id UUID NOT NULL,
    order_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    line_amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id),
    CONSTRAINT fk_order_lines_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT uq_order_lines_order_variant UNIQUE (order_id, variant_id),
    CONSTRAINT ck_order_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_lines_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_lines_amount CHECK (line_amount > 0)
);

CREATE TABLE order_consumer_inbox (
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    producer VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    purchase_request_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    source_topic VARCHAR(200) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    order_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_consumer_inbox PRIMARY KEY (event_id),
    CONSTRAINT fk_order_consumer_inbox_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT uq_order_consumer_inbox_source_position
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_order_consumer_inbox_event_type CHECK (event_type = 'PurchaseAccepted'),
    CONSTRAINT ck_order_consumer_inbox_event_version CHECK (event_version = 1),
    CONSTRAINT ck_order_consumer_inbox_producer CHECK (producer = 'flashsale-service'),
    CONSTRAINT ck_order_consumer_inbox_aggregate CHECK (aggregate_id = purchase_request_id),
    CONSTRAINT ck_order_consumer_inbox_aggregate_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_order_consumer_inbox_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_order_consumer_inbox_partition CHECK (source_partition >= 0),
    CONSTRAINT ck_order_consumer_inbox_offset CHECK (source_offset >= 0)
);

CREATE INDEX idx_order_consumer_inbox_purchase_request
    ON order_consumer_inbox (purchase_request_id);
CREATE INDEX idx_order_consumer_inbox_reservation
    ON order_consumer_inbox (reservation_id);

CREATE TABLE order_outbox_events (
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    payload JSONB NOT NULL,
    traceparent VARCHAR(256),
    tracestate VARCHAR(512),
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_by VARCHAR(200),
    claim_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_outbox_events PRIMARY KEY (event_id),
    CONSTRAINT uq_order_outbox_events_aggregate_event
        UNIQUE (aggregate_id, aggregate_version, event_type),
    CONSTRAINT ck_order_outbox_events_aggregate_type CHECK (aggregate_type = 'ORDER'),
    CONSTRAINT ck_order_outbox_events_aggregate_version CHECK (aggregate_version = 1),
    CONSTRAINT ck_order_outbox_events_type CHECK (event_type = 'OrderCreated'),
    CONSTRAINT ck_order_outbox_events_version CHECK (event_version = 1),
    CONSTRAINT ck_order_outbox_events_key CHECK (event_key = aggregate_id::text),
    CONSTRAINT ck_order_outbox_events_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED')),
    CONSTRAINT ck_order_outbox_events_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_order_outbox_events_due
    ON order_outbox_events (status, next_attempt_at, created_at);
CREATE INDEX idx_order_outbox_events_claim_recovery
    ON order_outbox_events (claim_until)
    WHERE status = 'IN_PROGRESS';

--rollback DROP TABLE order_outbox_events;
--rollback DROP TABLE order_consumer_inbox;
--rollback DROP TABLE order_lines;
--rollback DROP TABLE orders;
