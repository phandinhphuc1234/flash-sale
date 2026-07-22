--liquibase formatted sql

--changeset philia:002-create-product-admin-support dbms:postgresql runInTransaction:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:product_db SELECT current_database()

CREATE TABLE product_admin_idempotency_keys (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    actor_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_name VARCHAR(80) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    target_product_id UUID,
    http_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_product_admin_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_product_admin_idempotency_actor_key
        UNIQUE (actor_id, idempotency_key),
    CONSTRAINT fk_product_admin_idempotency_product
        FOREIGN KEY (target_product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_admin_idempotency_actor_not_blank
        CHECK (btrim(actor_id) <> ''),
    CONSTRAINT ck_product_admin_idempotency_key_not_blank
        CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_product_admin_idempotency_command_not_blank
        CHECK (btrim(command_name) <> ''),
    CONSTRAINT ck_product_admin_idempotency_request_hash_not_blank
        CHECK (btrim(request_hash) <> ''),
    CONSTRAINT ck_product_admin_idempotency_http_status
        CHECK (http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_product_admin_idempotency_response_object
        CHECK (jsonb_typeof(response_body) = 'object'),
    CONSTRAINT ck_product_admin_idempotency_expires_after_created
        CHECK (expires_at > created_at)
);

CREATE TABLE product_admin_audit_logs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    actor_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    command_name VARCHAR(80) NOT NULL,
    target_product_id UUID,
    outcome VARCHAR(30) NOT NULL,
    error_code VARCHAR(80),
    product_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_product_admin_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_product_admin_audit_product
        FOREIGN KEY (target_product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_admin_audit_actor_not_blank
        CHECK (btrim(actor_id) <> ''),
    CONSTRAINT ck_product_admin_audit_trace_not_blank
        CHECK (btrim(trace_id) <> ''),
    CONSTRAINT ck_product_admin_audit_command_not_blank
        CHECK (btrim(command_name) <> ''),
    CONSTRAINT ck_product_admin_audit_outcome
        CHECK (outcome IN ('SUCCESS', 'REPLAYED', 'REJECTED', 'CONFLICT')),
    CONSTRAINT ck_product_admin_audit_error_code_not_blank
        CHECK (error_code IS NULL OR btrim(error_code) <> ''),
    CONSTRAINT ck_product_admin_audit_version_non_negative
        CHECK (product_version IS NULL OR product_version >= 0)
);

CREATE INDEX idx_product_admin_idempotency_expires
    ON product_admin_idempotency_keys (expires_at);

CREATE INDEX idx_product_admin_audit_product_created
    ON product_admin_audit_logs (target_product_id, created_at DESC);

CREATE INDEX idx_product_admin_audit_actor_created
    ON product_admin_audit_logs (actor_id, created_at DESC);

CREATE INDEX idx_product_admin_audit_trace
    ON product_admin_audit_logs (trace_id);

--rollback DROP TABLE product_admin_audit_logs;
--rollback DROP TABLE product_admin_idempotency_keys;
