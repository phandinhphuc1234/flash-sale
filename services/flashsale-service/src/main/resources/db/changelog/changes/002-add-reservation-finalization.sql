--liquibase formatted sql

-- Reservation finalization is driven by idempotent commands from Order. The
-- inbox and outbox constraints preserve command identity and causal replay.
--changeset flashsale:002-add-reservation-finalization

ALTER TABLE flash_sale_reservations
    DROP CONSTRAINT ck_flash_sale_reservations_status;

ALTER TABLE flash_sale_reservations
    ADD CONSTRAINT ck_flash_sale_reservations_status
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED'));

ALTER TABLE flash_sale_reservations
    ADD COLUMN redis_reconciled_at TIMESTAMPTZ,
    ADD COLUMN finalized_at TIMESTAMPTZ;

CREATE TABLE reservation_command_inbox (
    command_id UUID NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    command_version INTEGER NOT NULL,
    producer VARCHAR(100) NOT NULL,
    saga_id UUID NOT NULL,
    order_id UUID NOT NULL,
    purchase_request_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    result_event_id UUID,
    source_topic VARCHAR(200) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reservation_command_inbox PRIMARY KEY (command_id),
    CONSTRAINT uq_reservation_command_inbox_reservation_command
        UNIQUE (reservation_id, command_type),
    CONSTRAINT uq_reservation_command_inbox_source_position
        UNIQUE (source_topic, source_partition, source_offset),
    CONSTRAINT ck_reservation_command_inbox_type CHECK (
        command_type IN ('ConfirmPurchaseReservation', 'ReleasePurchaseReservation')),
    CONSTRAINT ck_reservation_command_inbox_version CHECK (command_version = 1),
    CONSTRAINT ck_reservation_command_inbox_producer CHECK (producer = 'order-service'),
    CONSTRAINT ck_reservation_command_inbox_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_reservation_command_inbox_topic CHECK (
        source_topic = 'flashsale.purchase.commands.v1'),
    CONSTRAINT ck_reservation_command_inbox_partition CHECK (source_partition >= 0),
    CONSTRAINT ck_reservation_command_inbox_offset CHECK (source_offset >= 0)
);

ALTER TABLE flash_sale_outbox_events
    ADD COLUMN causation_id UUID;

ALTER TABLE flash_sale_outbox_events
    DROP CONSTRAINT uq_flash_sale_outbox_business_event,
    ADD CONSTRAINT ck_flash_sale_outbox_event_type CHECK (event_type IN (
        'PurchaseAccepted', 'PurchaseReservationConfirmed', 'PurchaseReservationReleased'));

CREATE UNIQUE INDEX uq_flash_sale_outbox_legacy_accepted
    ON flash_sale_outbox_events (aggregate_id, event_type, event_version)
    WHERE event_type = 'PurchaseAccepted' AND causation_id IS NULL;

CREATE UNIQUE INDEX uq_flash_sale_outbox_reservation_outcome_causation
    ON flash_sale_outbox_events (causation_id)
    WHERE causation_id IS NOT NULL
      AND event_type IN ('PurchaseReservationConfirmed', 'PurchaseReservationReleased');

--rollback DROP INDEX uq_flash_sale_outbox_reservation_outcome_causation;
--rollback DROP INDEX uq_flash_sale_outbox_legacy_accepted;
--rollback ALTER TABLE flash_sale_outbox_events DROP CONSTRAINT ck_flash_sale_outbox_event_type;
--rollback ALTER TABLE flash_sale_outbox_events ADD CONSTRAINT uq_flash_sale_outbox_business_event UNIQUE (aggregate_id, event_type, event_version);
--rollback ALTER TABLE flash_sale_outbox_events DROP COLUMN causation_id;
--rollback DROP TABLE reservation_command_inbox;
--rollback ALTER TABLE flash_sale_reservations DROP COLUMN finalized_at;
--rollback ALTER TABLE flash_sale_reservations DROP COLUMN redis_reconciled_at;
--rollback ALTER TABLE flash_sale_reservations DROP CONSTRAINT ck_flash_sale_reservations_status;
--rollback ALTER TABLE flash_sale_reservations ADD CONSTRAINT ck_flash_sale_reservations_status CHECK (status IN ('RESERVED', 'EXPIRED'));
