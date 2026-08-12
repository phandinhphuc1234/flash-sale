--liquibase formatted sql
--changeset flashsale:authentication-003-create-oauth-client-schema

-- Durable machine identities used by the OAuth2 Client Credentials flow.
CREATE TABLE oauth_clients (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    client_secret_hash VARCHAR(255) NOT NULL,
    grant_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    access_token_ttl_seconds INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_oauth_clients_client_id UNIQUE (client_id),
    CONSTRAINT ck_oauth_clients_client_id_not_blank CHECK (btrim(client_id) <> ''),
    CONSTRAINT ck_oauth_clients_secret_hash_argon2 CHECK (
        client_secret_hash LIKE '$argon2id$%'
    ),
    CONSTRAINT ck_oauth_clients_grant_type CHECK (grant_type = 'client_credentials'),
    CONSTRAINT ck_oauth_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_oauth_clients_ttl CHECK (
        access_token_ttl_seconds > 0 AND access_token_ttl_seconds <= 300
    )
);

CREATE INDEX idx_oauth_clients_status ON oauth_clients (status);

-- Least-privilege scopes assigned to each registered machine identity.
CREATE TABLE oauth_client_scopes (
    oauth_client_id UUID NOT NULL,
    scope VARCHAR(128) NOT NULL,
    CONSTRAINT pk_oauth_client_scopes PRIMARY KEY (oauth_client_id, scope),
    CONSTRAINT fk_oauth_client_scopes_client
        FOREIGN KEY (oauth_client_id) REFERENCES oauth_clients (id) ON DELETE CASCADE,
    CONSTRAINT ck_oauth_client_scopes_scope_not_blank CHECK (btrim(scope) <> '')
);

CREATE INDEX idx_oauth_client_scopes_scope ON oauth_client_scopes (scope);

--rollback DROP TABLE oauth_client_scopes;
--rollback DROP TABLE oauth_clients;
