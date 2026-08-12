--liquibase formatted sql
--changeset flashsale:authentication-001-create-schema

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    email_normalized VARCHAR(320) NOT NULL,
    username VARCHAR(100),
    username_normalized VARCHAR(100),
    password_hash VARCHAR(512) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_USER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized),
    CONSTRAINT uq_users_username_normalized UNIQUE (username_normalized),
    CONSTRAINT ck_users_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    device_name VARCHAR(150),
    user_agent VARCHAR(512),
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(100),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_sessions_status CHECK (status IN ('ACTIVE', 'REVOKED', 'COMPROMISED')),
    CONSTRAINT ck_user_sessions_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_user_sessions_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status IN ('REVOKED', 'COMPROMISED') AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_active_by_user
    ON user_sessions(user_id, expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    parent_token_id UUID,
    replaced_by_token_id UUID,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(100),
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES user_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_parent FOREIGN KEY (parent_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    CONSTRAINT fk_refresh_tokens_successor FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_parent_self CHECK (parent_token_id IS NULL OR parent_token_id <> id),
    CONSTRAINT ck_refresh_tokens_successor_self CHECK (replaced_by_token_id IS NULL OR replaced_by_token_id <> id),
    CONSTRAINT ck_refresh_tokens_successor_used CHECK (replaced_by_token_id IS NULL OR used_at IS NOT NULL)
);

CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE UNIQUE INDEX uq_refresh_tokens_current_per_session
    ON refresh_tokens(session_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

--rollback DROP TABLE refresh_tokens;
--rollback DROP TABLE user_sessions;
--rollback DROP TABLE users;
