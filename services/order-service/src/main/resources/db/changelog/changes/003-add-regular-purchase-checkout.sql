--liquibase formatted sql

-- Feature 049 is an expand-only schema step. It preserves all Flash Sale rows and allows the old
-- image to continue writing legacy rows while regular-purchase writers are still disabled.
--changeset order:003-add-regular-purchase-checkout

ALTER TABLE orders
    ALTER COLUMN reservation_id DROP NOT NULL,
    ALTER COLUMN campaign_id DROP NOT NULL,
    ADD COLUMN purchase_source VARCHAR(16) NOT NULL DEFAULT 'FLASH_SALE',
    ADD COLUMN stock_participant_type VARCHAR(32) NOT NULL DEFAULT 'FLASH_SALE_RESERVATION',
    ADD COLUMN stock_reference_id UUID,
    ADD COLUMN cart_id UUID,
    ADD COLUMN cart_version BIGINT;

UPDATE orders
SET stock_reference_id = reservation_id
WHERE stock_reference_id IS NULL;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_purchase_source
        CHECK (purchase_source IN ('FLASH_SALE', 'BUY_NOW', 'CART')),
    ADD CONSTRAINT ck_orders_stock_participant_type
        CHECK (stock_participant_type IN ('FLASH_SALE_RESERVATION', 'REGULAR_STOCK_HOLD')),
    ADD CONSTRAINT ck_orders_cart_version_non_negative
        CHECK (cart_version IS NULL OR cart_version >= 0);

CREATE TABLE regular_purchase_requests (
    id UUID PRIMARY KEY,
    shopper_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    source VARCHAR(16) NOT NULL,
    state VARCHAR(32) NOT NULL,
    proposed_order_id UUID NOT NULL,
    proposed_hold_id UUID NOT NULL,
    cart_id UUID,
    cart_version BIGINT,
    snapshot_payload JSONB NOT NULL,
    hold_expires_at TIMESTAMPTZ,
    order_id UUID,
    rejection_code VARCHAR(64),
    rejection_payload JSONB,
    response_payload JSONB,
    traceparent VARCHAR(256),
    tracestate VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_regular_purchase_requests_shopper_key UNIQUE (shopper_id, idempotency_key),
    CONSTRAINT uq_regular_purchase_requests_order UNIQUE (order_id),
    CONSTRAINT uq_regular_purchase_requests_proposed_order UNIQUE (proposed_order_id),
    CONSTRAINT uq_regular_purchase_requests_proposed_hold UNIQUE (proposed_hold_id),
    CONSTRAINT fk_regular_purchase_requests_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT ck_regular_purchase_requests_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_regular_purchase_requests_source CHECK (source IN ('BUY_NOW', 'CART')),
    CONSTRAINT ck_regular_purchase_requests_state CHECK (state IN (
        'RECEIVED', 'SNAPSHOT_VALIDATED', 'PRODUCT_VALIDATED', 'HOLD_ACQUIRED', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_regular_purchase_requests_cart_source CHECK (
        (source = 'BUY_NOW' AND cart_id IS NULL AND cart_version IS NULL)
        OR (source = 'CART' AND cart_id IS NOT NULL AND cart_version IS NOT NULL AND cart_version >= 0)
    ),
    CONSTRAINT ck_regular_purchase_requests_accepted_response CHECK (
        state <> 'ACCEPTED' OR (order_id IS NOT NULL AND response_payload IS NOT NULL)
    ),
    CONSTRAINT ck_regular_purchase_requests_rejected_code CHECK (
        state <> 'REJECTED' OR rejection_code IS NOT NULL
    )
);

CREATE INDEX idx_regular_purchase_requests_recovery
    ON regular_purchase_requests (state, updated_at, id)
    WHERE state IN ('RECEIVED', 'SNAPSHOT_VALIDATED', 'PRODUCT_VALIDATED', 'HOLD_ACQUIRED');

ALTER TABLE order_lines
    ADD COLUMN product_id UUID,
    ADD COLUMN sku_snapshot VARCHAR(100),
    ADD COLUMN display_name_snapshot VARCHAR(300);

ALTER TABLE purchase_sagas
    ALTER COLUMN reservation_id DROP NOT NULL,
    ADD COLUMN stock_participant_type VARCHAR(32) NOT NULL DEFAULT 'FLASH_SALE_RESERVATION',
    ADD COLUMN stock_reference_id UUID;

UPDATE purchase_sagas
SET stock_reference_id = reservation_id
WHERE stock_reference_id IS NULL;

ALTER TABLE purchase_sagas
    DROP CONSTRAINT ck_purchase_sagas_status,
    ADD CONSTRAINT ck_purchase_sagas_status CHECK (status IN (
        'PAYMENT_PENDING', 'CONFIRMING_RESERVATION', 'RELEASING_RESERVATION',
        'CONFIRMING_STOCK', 'RELEASING_STOCK', 'COMPLETED', 'COMPENSATED', 'MANUAL_REVIEW')),
    ADD CONSTRAINT ck_purchase_sagas_stock_participant_type
        CHECK (stock_participant_type IN ('FLASH_SALE_RESERVATION', 'REGULAR_STOCK_HOLD'));

ALTER TABLE purchase_saga_inbox
    DROP CONSTRAINT ck_purchase_saga_inbox_event_type,
    DROP CONSTRAINT ck_purchase_saga_inbox_producer,
    ADD CONSTRAINT ck_purchase_saga_inbox_event_type CHECK (event_type IN (
        'PaymentSucceeded', 'PaymentFailed',
        'PurchaseReservationConfirmed', 'PurchaseReservationReleased',
        'RegularStockHoldConfirmed', 'RegularStockHoldReleased', 'RegularStockHoldExpired')),
    ADD CONSTRAINT ck_purchase_saga_inbox_producer CHECK (
        (event_type IN ('PaymentSucceeded', 'PaymentFailed') AND producer = 'payment-service')
        OR (event_type IN ('PurchaseReservationConfirmed', 'PurchaseReservationReleased')
            AND producer = 'flashsale-service')
        OR (event_type IN ('RegularStockHoldConfirmed', 'RegularStockHoldReleased', 'RegularStockHoldExpired')
            AND producer = 'inventory-service')
    );

ALTER TABLE order_outbox_events
    DROP CONSTRAINT ck_order_outbox_events_type,
    ADD CONSTRAINT ck_order_outbox_events_type CHECK (
        (aggregate_type = 'ORDER' AND event_type IN (
            'OrderCreated', 'OrderConfirmed', 'OrderCancelled', 'OrderExpired',
            'OrderPaymentReviewRequired', 'OrderCreatedV2', 'OrderConfirmedV2',
            'OrderCancelledV2', 'OrderExpiredV2', 'ReconcilePurchasedCartSnapshot'))
        OR (aggregate_type = 'PURCHASE_SAGA' AND event_type IN (
            'PaymentRequested', 'ConfirmPurchaseReservation', 'ReleasePurchaseReservation',
            'ConfirmRegularStockHold', 'ReleaseRegularStockHold'))
    );
