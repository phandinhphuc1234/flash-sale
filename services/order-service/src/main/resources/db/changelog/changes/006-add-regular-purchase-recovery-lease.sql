--liquibase formatted sql

-- Feature 049 T087: operational lease fields keep multiple Order instances from recovering the
-- same stale intake concurrently. The fields are additive and do not alter business checkpoints.
--changeset order:006-add-regular-purchase-recovery-lease

ALTER TABLE regular_purchase_requests
    ADD COLUMN recovery_lease_owner VARCHAR(128),
    ADD COLUMN recovery_lease_until TIMESTAMPTZ,
    ADD COLUMN recovery_attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE regular_purchase_requests
    ADD CONSTRAINT ck_regular_purchase_requests_recovery_attempts
        CHECK (recovery_attempt_count >= 0),
    ADD CONSTRAINT ck_regular_purchase_requests_recovery_lease_pair
        CHECK ((recovery_lease_owner IS NULL AND recovery_lease_until IS NULL)
            OR (recovery_lease_owner IS NOT NULL AND recovery_lease_until IS NOT NULL));

CREATE INDEX idx_regular_purchase_requests_recovery_lease
    ON regular_purchase_requests (state, recovery_lease_until, updated_at, id)
    WHERE state IN ('RECEIVED', 'SNAPSHOT_VALIDATED', 'PRODUCT_VALIDATED', 'HOLD_ACQUIRED');
