--liquibase formatted sql

-- The Flash Sale database owns durable acceptance, reservations, idempotency, and publication intent.
-- No foreign key in this migration crosses a service boundary.

--changeset flashsale:001-create-purchase-requests
CREATE TABLE purchase_requests (
    id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_purchase_requests PRIMARY KEY (id),
    CONSTRAINT uq_purchase_requests_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_purchase_requests_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_requests_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_purchase_requests_outcome CHECK (outcome IN ('ACCEPTED', 'EXPIRED')),
    CONSTRAINT ck_purchase_requests_accepted_at CHECK (
        (outcome = 'ACCEPTED' AND accepted_at IS NOT NULL)
        OR (outcome = 'EXPIRED')
    )
);

--changeset flashsale:001-purchase-request-indexes
CREATE INDEX idx_purchase_requests_owner_created
    ON purchase_requests (user_id, created_at DESC);
CREATE INDEX idx_purchase_requests_campaign_expiry
    ON purchase_requests (campaign_id, outcome, expires_at);

--changeset flashsale:001-create-reservations
CREATE TABLE flash_sale_reservations (
    id UUID NOT NULL,
    purchase_request_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    inventory_allocation_id UUID NOT NULL,
    sku_snapshot VARCHAR(120) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_flash_sale_reservations PRIMARY KEY (id),
    CONSTRAINT uq_flash_sale_reservations_purchase UNIQUE (purchase_request_id),
    CONSTRAINT ck_flash_sale_reservations_price CHECK (unit_price >= 0),
    CONSTRAINT ck_flash_sale_reservations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_flash_sale_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_flash_sale_reservations_status CHECK (status IN ('RESERVED', 'EXPIRED')),
    CONSTRAINT ck_flash_sale_reservations_version CHECK (version >= 0)
);

--changeset flashsale:001-reservation-indexes
CREATE INDEX idx_flash_sale_reservations_owner
    ON flash_sale_reservations (user_id, id);
CREATE INDEX idx_flash_sale_reservations_due
    ON flash_sale_reservations (status, expires_at)
    WHERE status = 'RESERVED';

--changeset flashsale:001-create-idempotency-records
CREATE TABLE purchase_idempotency_records (
    user_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    purchase_request_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    retained_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_purchase_idempotency_records
        PRIMARY KEY (user_id, campaign_id, idempotency_key_hash),
    CONSTRAINT uq_purchase_idempotency_purchase UNIQUE (purchase_request_id),
    CONSTRAINT ck_purchase_idempotency_key_hash CHECK (idempotency_key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_purchase_idempotency_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

--changeset flashsale:001-idempotency-indexes
CREATE INDEX idx_purchase_idempotency_retained_until
    ON purchase_idempotency_records (retained_until);

--changeset flashsale:001-create-outbox
CREATE TABLE flash_sale_outbox_events (
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_by VARCHAR(200),
    claim_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_flash_sale_outbox_events PRIMARY KEY (event_id),
    CONSTRAINT uq_flash_sale_outbox_business_event
        UNIQUE (aggregate_id, event_type, event_version),
    CONSTRAINT ck_flash_sale_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED')),
    CONSTRAINT ck_flash_sale_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_flash_sale_outbox_versions CHECK (aggregate_version >= 0 AND event_version > 0),
    CONSTRAINT ck_flash_sale_outbox_published_at CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR status <> 'PUBLISHED'
    )
);

--changeset flashsale:001-outbox-indexes
CREATE INDEX idx_flash_sale_outbox_due
    ON flash_sale_outbox_events (next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_flash_sale_outbox_claims
    ON flash_sale_outbox_events (status, claim_until);

--rollback DROP TABLE flash_sale_outbox_events;
--rollback DROP TABLE purchase_idempotency_records;
--rollback DROP TABLE flash_sale_reservations;
--rollback DROP TABLE purchase_requests;
