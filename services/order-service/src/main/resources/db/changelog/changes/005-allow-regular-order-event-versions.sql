--liquibase formatted sql

-- OrderCreatedV2 is additive for regular checkout. Keep every legacy V1 event exactly on version
-- one and admit only the documented V2 lifecycle facts at version two. The immutable 003
-- migration must not be edited after any environment has applied it.
--changeset order:005-allow-regular-order-event-versions

ALTER TABLE order_outbox_events
    DROP CONSTRAINT ck_order_outbox_events_version,
    ADD CONSTRAINT ck_order_outbox_events_version CHECK (
        (event_type IN (
            'OrderCreated', 'OrderConfirmed', 'OrderCancelled', 'OrderExpired',
            'OrderPaymentReviewRequired', 'PaymentRequested', 'ConfirmPurchaseReservation',
            'ReleasePurchaseReservation', 'ConfirmRegularStockHold', 'ReleaseRegularStockHold',
            'ReconcilePurchasedCartSnapshot'
        ) AND event_version = 1)
        OR (event_type IN (
            'OrderCreatedV2', 'OrderConfirmedV2', 'OrderCancelledV2', 'OrderExpiredV2'
        ) AND event_version = 2)
    );
