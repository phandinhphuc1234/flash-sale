--liquibase formatted sql

--changeset philia:payment-002-add-outbox-error
ALTER TABLE payment_outbox_events
    ADD COLUMN last_error_code VARCHAR(128);

--rollback ALTER TABLE payment_outbox_events DROP COLUMN IF EXISTS last_error_code;
