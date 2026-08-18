--liquibase formatted sql

--changeset flashsale:003-add-payment-recovery-payment-unique
-- Keep deadline work unique when no attempt exists. PostgreSQL permits multiple NULLs in
-- the attempt-scoped index from the core migration, so the payment-scoped partial index
-- closes that duplicate scheduling window without changing completed history.
CREATE UNIQUE INDEX uk_payment_recovery_active_payment_work_no_attempt
    ON payment_recovery_work (payment_id, work_type)
    WHERE attempt_id IS NULL AND status IN ('PENDING', 'IN_PROGRESS');

--rollback DROP INDEX IF EXISTS uk_payment_recovery_active_payment_work_no_attempt;
