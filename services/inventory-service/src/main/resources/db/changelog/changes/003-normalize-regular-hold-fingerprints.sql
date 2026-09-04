--liquibase formatted sql

-- A fixed PostgreSQL CHAR value is right-padded on read. Store deterministic SHA-256 fingerprints
-- as VARCHAR instead so JPA schema validation and byte-for-byte replay identity use the same value.
-- This is expand-safe for rows created by the additive 002 migration: every valid fingerprint is
-- already exactly 64 characters and the existing check constraints remain in force.
--changeset flashsale:inventory-003-normalize-regular-hold-fingerprints

ALTER TABLE regular_stock_holds
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64);

ALTER TABLE regular_hold_command_inbox
    ALTER COLUMN payload_fingerprint TYPE VARCHAR(64);
