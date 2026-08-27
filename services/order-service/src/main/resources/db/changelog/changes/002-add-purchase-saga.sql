--liquibase formatted sql

-- Durable Order-owned purchase saga state and inbox. The IDs are intentionally
-- opaque service-boundary references; only the local order FK is enforced.
--changeset order:002-add-purchase-saga

ALTER TABLE orders
    DROP CONSTRAINT ck_orders_status;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));

CREATE TABLE purchase_sagas (
    id UUID NOT NULL,
    order_id UUID NOT NULL,
    purchase_request_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    payment_deadline TIMESTAMPTZ NOT NULL,
    payment_id UUID,
    last_payment_version BIGINT,
    payment_succeeded_at TIMESTAMPTZ,
    payment_failure_reason VARCHAR(64),
    desired_order_status VARCHAR(32),
    active_command_id UUID,
    step_started_at TIMESTAMPTZ NOT NULL,
    manual_review_reason VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_purchase_sagas PRIMARY KEY (id),
    CONSTRAINT uq_purchase_sagas_order UNIQUE (order_id),
    CONSTRAINT uq_purchase_sagas_purchase_request UNIQUE (purchase_request_id),
    CONSTRAINT uq_purchase_sagas_reservation UNIQUE (reservation_id),
    CONSTRAINT fk_purchase_sagas_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_purchase_sagas_identity CHECK (id = purchase_request_id),
    CONSTRAINT ck_purchase_sagas_status CHECK (status IN (
        'PAYMENT_PENDING', 'CONFIRMING_RESERVATION', 'RELEASING_RESERVATION',
        'COMPLETED', 'COMPENSATED', 'MANUAL_REVIEW')),
    CONSTRAINT ck_purchase_sagas_deadline CHECK (payment_deadline > created_at),
    CONSTRAINT ck_purchase_sagas_payment_version CHECK (
        last_payment_version IS NULL OR last_payment_version > 0),
    CONSTRAINT ck_purchase_sagas_desired_status CHECK (
        desired_order_status IS NULL OR desired_order_status IN ('CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_purchase_sagas_version CHECK (version >= 0)
);

CREATE INDEX idx_purchase_sagas_order
    ON purchase_sagas (order_id);
CREATE INDEX idx_purchase_sagas_deadline
    ON purchase_sagas (status, payment_deadline)
    WHERE status = 'PAYMENT_PENDING';

CREATE TABLE purchase_saga_inbox (
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    producer VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    order_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    source_topic VARCHAR(200) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_purchase_saga_inbox PRIMARY KEY (event_id),
    CONSTRAINT fk_purchase_saga_inbox_order
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT uq_purchase_saga_inbox_source_position
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_purchase_saga_inbox_event_type CHECK (event_type IN (
        'PaymentSucceeded', 'PaymentFailed',
        'PurchaseReservationConfirmed', 'PurchaseReservationReleased')),
    CONSTRAINT ck_purchase_saga_inbox_event_version CHECK (event_version = 1),
    CONSTRAINT ck_purchase_saga_inbox_producer CHECK (
        (event_type IN ('PaymentSucceeded', 'PaymentFailed') AND producer = 'payment-service')
        OR (event_type IN ('PurchaseReservationConfirmed', 'PurchaseReservationReleased')
            AND producer = 'flashsale-service')),
    CONSTRAINT ck_purchase_saga_inbox_aggregate_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_purchase_saga_inbox_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_purchase_saga_inbox_partition CHECK (source_partition >= 0),
    CONSTRAINT ck_purchase_saga_inbox_offset CHECK (source_offset >= 0)
);

CREATE INDEX idx_purchase_saga_inbox_order
    ON purchase_saga_inbox (order_id, processed_at);
CREATE INDEX idx_purchase_saga_inbox_aggregate_version
    ON purchase_saga_inbox (aggregate_id, aggregate_version);

ALTER TABLE order_outbox_events
    DROP CONSTRAINT ck_order_outbox_events_aggregate_type,
    DROP CONSTRAINT ck_order_outbox_events_aggregate_version,
    DROP CONSTRAINT ck_order_outbox_events_type,
    DROP CONSTRAINT ck_order_outbox_events_key;

ALTER TABLE order_outbox_events
    ADD CONSTRAINT ck_order_outbox_events_aggregate_type
        CHECK (aggregate_type IN ('ORDER', 'PURCHASE_SAGA')),
    ADD CONSTRAINT ck_order_outbox_events_aggregate_version
        CHECK (aggregate_version > 0),
    ADD CONSTRAINT ck_order_outbox_events_type
        CHECK (
            (aggregate_type = 'ORDER' AND event_type IN (
                'OrderCreated', 'OrderConfirmed', 'OrderCancelled', 'OrderExpired',
                'OrderPaymentReviewRequired'))
            OR (aggregate_type = 'PURCHASE_SAGA' AND event_type IN (
                'PaymentRequested', 'ConfirmPurchaseReservation', 'ReleasePurchaseReservation'))),
    ADD CONSTRAINT ck_order_outbox_events_key
        CHECK (event_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$');

--rollback ALTER TABLE order_outbox_events DROP CONSTRAINT ck_order_outbox_events_key;
--rollback ALTER TABLE order_outbox_events DROP CONSTRAINT ck_order_outbox_events_type;
--rollback ALTER TABLE order_outbox_events DROP CONSTRAINT ck_order_outbox_events_aggregate_version;
--rollback ALTER TABLE order_outbox_events DROP CONSTRAINT ck_order_outbox_events_aggregate_type;
--rollback ALTER TABLE order_outbox_events ADD CONSTRAINT ck_order_outbox_events_aggregate_type CHECK (aggregate_type = 'ORDER');
--rollback ALTER TABLE order_outbox_events ADD CONSTRAINT ck_order_outbox_events_aggregate_version CHECK (aggregate_version = 1);
--rollback ALTER TABLE order_outbox_events ADD CONSTRAINT ck_order_outbox_events_type CHECK (event_type = 'OrderCreated');
--rollback ALTER TABLE order_outbox_events ADD CONSTRAINT ck_order_outbox_events_key CHECK (event_key = aggregate_id::text);
--rollback DROP INDEX idx_purchase_saga_inbox_aggregate_version;
--rollback DROP INDEX idx_purchase_saga_inbox_order;
--rollback DROP TABLE purchase_saga_inbox;
--rollback DROP INDEX idx_purchase_sagas_deadline;
--rollback DROP INDEX idx_purchase_sagas_order;
--rollback DROP TABLE purchase_sagas;
--rollback ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
--rollback ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (status = 'PENDING_PAYMENT');
