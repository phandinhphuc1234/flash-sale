--liquibase formatted sql

-- Keep the durable fingerprint aligned with the JPA mapping. CHAR(64) pads
-- values with spaces in PostgreSQL, while the application treats the SHA-256
-- fingerprint as an exact variable-length text value.
--changeset flashsale:003-normalize-reservation-inbox-fingerprint

ALTER TABLE reservation_command_inbox
    ALTER COLUMN payload_fingerprint TYPE VARCHAR(64)
    USING btrim(payload_fingerprint);

--rollback ALTER TABLE reservation_command_inbox
--rollback     ALTER COLUMN payload_fingerprint TYPE CHAR(64)
--rollback     USING rpad(payload_fingerprint, 64, ' ');
