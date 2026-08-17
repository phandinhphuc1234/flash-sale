--liquibase formatted sql

--changeset philia:payment-001-create-core-schema

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    payment_deadline TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'UNKNOWN', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    failure_reason VARCHAR(64) CHECK (failure_reason IS NULL OR failure_reason IN ('PAYMENT_DEADLINE_EXPIRED', 'CHECKOUT_ATTEMPT_LIMIT_REACHED', 'PROVIDER_TERMINAL_FAILURE')),
    succeeded_at TIMESTAMPTZ,
    aggregate_version BIGINT NOT NULL DEFAULT 0 CHECK (aggregate_version >= 0),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_payments_user_created ON payments (user_id, created_at DESC);
CREATE INDEX ix_payments_deadline ON payments (payment_deadline, status);
CREATE INDEX ix_payments_status_updated ON payments (status, updated_at);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 3),
    status VARCHAR(32) NOT NULL CHECK (status IN ('CREATING', 'OPEN', 'PROCESSING', 'UNKNOWN', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    provider VARCHAR(16) NOT NULL CHECK (provider = 'STRIPE'),
    provider_idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    provider_session_id VARCHAR(255) UNIQUE,
    provider_payment_intent_id VARCHAR(255),
    first_submitted_at TIMESTAMPTZ,
    safe_replay_until TIMESTAMPTZ,
    provider_expires_at TIMESTAMPTZ,
    last_provider_state VARCHAR(32),
    failure_reason VARCHAR(64) CHECK (failure_reason IS NULL OR failure_reason IN ('PAYMENT_DEADLINE_EXPIRED', 'CHECKOUT_ATTEMPT_LIMIT_REACHED', 'PROVIDER_TERMINAL_FAILURE')),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payment_attempt_number UNIQUE (payment_id, attempt_number)
);

CREATE INDEX ix_payment_attempts_payment ON payment_attempts (payment_id, attempt_number);
CREATE INDEX ix_payment_attempts_payment_intent ON payment_attempts (provider_payment_intent_id)
    WHERE provider_payment_intent_id IS NOT NULL;
CREATE INDEX ix_payment_attempts_replay ON payment_attempts (safe_replay_until, status);
CREATE UNIQUE INDEX uk_payment_attempt_one_unresolved
    ON payment_attempts (payment_id)
    WHERE status IN ('CREATING', 'OPEN', 'PROCESSING', 'UNKNOWN');

CREATE TABLE payment_command_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL,
    order_id UUID NOT NULL UNIQUE,
    payload_fingerprint CHAR(64) NOT NULL,
    payment_id UUID REFERENCES payments(id),
    processing_status VARCHAR(24) NOT NULL CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'CONFLICTED')),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE INDEX ix_payment_command_inbox_status ON payment_command_inbox (processing_status, received_at);

CREATE TABLE payment_client_idempotency (
    id UUID PRIMARY KEY,
    operation VARCHAR(64) NOT NULL CHECK (operation = 'CREATE_OR_RESUME_CHECKOUT'),
    key_digest CHAR(64) NOT NULL,
    user_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payments(id),
    request_fingerprint CHAR(64) NOT NULL,
    attempt_id UUID REFERENCES payment_attempts(id),
    outcome_status VARCHAR(24) NOT NULL CHECK (outcome_status IN ('ACCEPTED', 'AVAILABLE', 'RECOVERING')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payment_client_idempotency_key UNIQUE (operation, key_digest)
);

CREATE INDEX ix_payment_client_idempotency_payment ON payment_client_idempotency (payment_id, user_id);

CREATE TABLE payment_provider_event_receipts (
    id UUID PRIMARY KEY,
    provider_event_id VARCHAR(255) NOT NULL UNIQUE,
    provider_event_type VARCHAR(128) NOT NULL,
    provider_api_version VARCHAR(32),
    live_mode BOOLEAN NOT NULL,
    provider_object_id VARCHAR(255),
    payment_id UUID REFERENCES payments(id),
    attempt_id UUID REFERENCES payment_attempts(id),
    order_id UUID,
    provider_created_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    processing_status VARCHAR(24) NOT NULL CHECK (processing_status IN ('PENDING', 'IN_PROGRESS', 'PROCESSED', 'IGNORED', 'MANUAL_REVIEW')),
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(64),
    processed_at TIMESTAMPTZ
);

CREATE INDEX ix_payment_provider_receipts_claim
    ON payment_provider_event_receipts (processing_status, next_attempt_at, lease_until);
CREATE INDEX ix_payment_provider_receipts_payment ON payment_provider_event_receipts (payment_id, verified_at);

CREATE TABLE payment_recovery_work (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    attempt_id UUID REFERENCES payment_attempts(id),
    work_type VARCHAR(32) NOT NULL CHECK (work_type IN ('CREATE_SESSION', 'REFRESH_SESSION', 'EXPIRE_SESSION')),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'MANUAL_REVIEW')),
    provider_idempotency_key VARCHAR(255),
    safe_replay_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_payment_recovery_claim
    ON payment_recovery_work (status, next_attempt_at, lease_until);
CREATE UNIQUE INDEX uk_payment_recovery_active_work
    ON payment_recovery_work (attempt_id, work_type)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

CREATE TABLE payment_outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL REFERENCES payments(id),
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version > 0),
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN ('PaymentSucceeded', 'PaymentFailed')),
    event_version INTEGER NOT NULL CHECK (event_version = 1),
    topic_name VARCHAR(255) NOT NULL,
    message_key UUID NOT NULL,
    payload JSONB NOT NULL,
    traceparent VARCHAR(255),
    tracestate VARCHAR(255),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payment_outbox_version_type UNIQUE (aggregate_id, aggregate_version, event_type)
);

CREATE INDEX ix_payment_outbox_claim
    ON payment_outbox_events (status, next_attempt_at, lease_until);

COMMENT ON TABLE payments IS 'Payment durable truth. Card data and provider secrets are intentionally not stored.';
COMMENT ON TABLE payment_attempts IS 'Provider identity only; hosted Checkout address and customer card data are intentionally absent.';
COMMENT ON TABLE payment_provider_event_receipts IS 'Verified provider metadata only; raw webhook body and signature are intentionally absent.';
COMMENT ON TABLE payment_outbox_events IS 'Safe versioned facts only; credentials, tokens, URLs, and provider response bodies are intentionally absent.';

--rollback DROP TABLE IF EXISTS payment_outbox_events, payment_recovery_work, payment_provider_event_receipts, payment_client_idempotency, payment_command_inbox, payment_attempts, payments CASCADE;
