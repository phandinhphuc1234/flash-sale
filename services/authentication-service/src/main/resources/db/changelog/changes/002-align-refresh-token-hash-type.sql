--liquibase formatted sql
--changeset flashsale:authentication-002-align-refresh-token-hash-type

ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64)
    USING token_hash::VARCHAR(64);

--rollback ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE CHAR(64) USING token_hash::CHAR(64);
