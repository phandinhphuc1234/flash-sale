# Data Model: Authentication Session MVP

**Feature**: [015 Authentication Session MVP](spec.md)
**Owning service/schema**: `authentication-service` / `auth_db`
**Durable source of truth**: PostgreSQL
**Ephemeral abuse state**: the existing root-owned Redis instance

## 1. Domain model and aggregate boundaries

### Account aggregate

`Account` owns authentication identity, normalized login identifiers, password proof, role, and
account state. It does not own profile, cart, order, payment, address, or campaign data.

Invariants:

- one normalized email identifies at most one account;
- one non-null normalized username identifies at most one account;
- public registration always creates `ROLE_USER` and cannot accept role/authority assignment;
- passwords exist in durable state only as Argon2id encoded hashes;
- only an `ACTIVE` account can establish a login session;
- `LOCKED` and `DISABLED` are deliberately indistinguishable through the public login response.

### LoginSession aggregate

`LoginSession` is the transaction/concurrency owner for refresh rotation and revocation. A session
represents one login on one browser/device and contains the lifecycle of its `RefreshCredential`
children.

Invariants:

- an active session has at most one current refresh credential;
- a refresh credential succeeds at most once;
- session absolute expiry cannot be extended by refresh;
- compromise/revocation prevents every credential in that session from issuing another access token;
- an account may have multiple sessions, and logout-all revokes all of them by verified account ID.

## 2. PostgreSQL schema

The implementation migration remains service-owned at:

```text
services/authentication-service/src/main/resources/db/changelog/changes/001-create-authentication-schema.sql
```

The master YAML includes this PostgreSQL formatted-SQL changeset, matching the existing repository
Liquibase convention.

### 2.1 `users`

| Column | PostgreSQL type | Null | Rule/purpose |
|--------|-----------------|------|--------------|
| `id` | `UUID` | no | Primary key and JWT `sub` |
| `email` | `VARCHAR(320)` | no | Preserved display form after outer identifier trim |
| `email_normalized` | `VARCHAR(320)` | no | `trim().toLowerCase(Locale.ROOT)` lookup/uniqueness value |
| `username` | `VARCHAR(100)` | yes | Optional preserved display form |
| `username_normalized` | `VARCHAR(100)` | yes | Trimmed/lowercase value when username is supplied |
| `password_hash` | `VARCHAR(512)` | no | Full encoded Argon2id value; never returned/logged |
| `role` | `VARCHAR(32)` | no | `ROLE_USER` or `ROLE_ADMIN`; default `ROLE_USER` |
| `status` | `VARCHAR(32)` | no | `ACTIVE`, `LOCKED`, or `DISABLED`; default `ACTIVE` |
| `locked_until` | `TIMESTAMPTZ` | yes | Optional durable lock end; unrelated to Redis cooldown |
| `last_login_at` | `TIMESTAMPTZ` | yes | Most recent committed login |
| `created_at` | `TIMESTAMPTZ` | no | Creation instant |
| `updated_at` | `TIMESTAMPTZ` | no | Last durable account update |

Constraints and indexes:

```sql
PRIMARY KEY (id)
UNIQUE (email_normalized)
UNIQUE (username_normalized)
CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;
```

PostgreSQL permits multiple `NULL` values in the username unique constraint, which matches optional
username semantics. A uniqueness exception caused by a concurrent register is translated to the same
409 as a pre-check duplicate.

### 2.2 `user_sessions`

| Column | PostgreSQL type | Null | Rule/purpose |
|--------|-----------------|------|--------------|
| `id` | `UUID` | no | Session identity |
| `user_id` | `UUID` | no | Owning `users.id` |
| `status` | `VARCHAR(32)` | no | `ACTIVE`, `REVOKED`, or `COMPROMISED` |
| `device_name` | `VARCHAR(150)` | yes | Optional caller-supplied display label, length bounded |
| `user_agent` | `VARCHAR(512)` | yes | Bounded HTTP User-Agent snapshot; never used as identity |
| `ip_address` | `INET` | yes | Direct peer address observed at login; retained with session |
| `created_at` | `TIMESTAMPTZ` | no | Login/session creation |
| `last_activity_at` | `TIMESTAMPTZ` | yes | Latest committed successful refresh |
| `expires_at` | `TIMESTAMPTZ` | no | Absolute session boundary, `created_at + 30 days` |
| `revoked_at` | `TIMESTAMPTZ` | yes | Revocation/compromise instant |
| `revoke_reason` | `VARCHAR(100)` | yes | Stable internal reason; not returned directly |

Constraints and indexes:

```sql
PRIMARY KEY (id)
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
CHECK (status IN ('ACTIVE', 'REVOKED', 'COMPROMISED'))
CHECK (expires_at > created_at)
CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
    OR (status IN ('REVOKED', 'COMPROMISED') AND revoked_at IS NOT NULL))
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_active_by_user
  ON user_sessions(user_id, expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
```

No stored `EXPIRED` status exists. `expires_at <= now` makes a session logically expired.

### 2.3 `refresh_tokens`

The database table retains the conventional transport term `refresh_tokens`; the domain term is
`RefreshCredential` to emphasize that only a digest is stored.

| Column | PostgreSQL type | Null | Rule/purpose |
|--------|-----------------|------|--------------|
| `id` | `UUID` | no | Credential record identity |
| `session_id` | `UUID` | no | Owning session |
| `token_hash` | `CHAR(64)` | no | Unique lowercase SHA-256 hex; never raw token |
| `parent_token_id` | `UUID` | yes | Predecessor in the rotation chain |
| `replaced_by_token_id` | `UUID` | yes | Successor created by successful rotation |
| `issued_at` | `TIMESTAMPTZ` | no | Issue instant |
| `expires_at` | `TIMESTAMPTZ` | no | `min(issued_at + 7 days, session.expires_at)` |
| `used_at` | `TIMESTAMPTZ` | yes | First successful rotation use |
| `revoked_at` | `TIMESTAMPTZ` | yes | Revocation/compromise instant |
| `revoke_reason` | `VARCHAR(100)` | yes | Stable internal reason |

Constraints and indexes:

```sql
PRIMARY KEY (id)
FOREIGN KEY (session_id) REFERENCES user_sessions(id) ON DELETE CASCADE
FOREIGN KEY (parent_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
UNIQUE (token_hash)
CHECK (token_hash ~ '^[0-9a-f]{64}$')
CHECK (expires_at > issued_at)
CHECK (parent_token_id IS NULL OR parent_token_id <> id)
CHECK (replaced_by_token_id IS NULL OR replaced_by_token_id <> id)
CHECK (replaced_by_token_id IS NULL OR used_at IS NOT NULL)
CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE UNIQUE INDEX uq_refresh_tokens_current_per_session
  ON refresh_tokens(session_id)
  WHERE used_at IS NULL AND revoked_at IS NULL;
```

Derived state:

| Derived state | Predicate |
|---------------|-----------|
| `CURRENT` | `used_at IS NULL`, `revoked_at IS NULL`, and `expires_at > now` |
| `ROTATED` | `used_at IS NOT NULL` and `replaced_by_token_id IS NOT NULL` |
| `REVOKED` | `revoked_at IS NOT NULL` |
| `EXPIRED` | `expires_at <= now` |

## 3. Transaction and concurrency boundaries

### Registration transaction

1. Normalize email and optional username.
2. Check known duplicates for a useful early 409.
3. Encode password and insert one `users` row as `ROLE_USER/ACTIVE`.
4. Commit. Translate any unique-constraint race to `AUTH_ACCOUNT_ALREADY_EXISTS`.

### Successful login transaction

Password verification and Redis pre-check occur before the durable transaction. Immediately before
commit, cooldown is checked again. The transaction then:

1. updates `users.last_login_at`/`updated_at`;
2. inserts one `user_sessions` row with a 30-day absolute expiry;
3. inserts one current `refresh_tokens` digest with expiry capped at seven days;
4. commits all three effects together.

Raw refresh material is returned to the web adapter only after the digest-bearing transaction
commits. If response delivery fails, the client may log in again; no raw value can be recovered from
the database.

### Refresh rotation transaction

```text
digest(raw cookie)
  -> SELECT refresh row + session FOR UPDATE
  -> validate account/session/credential/time
  -> mark old used (frees unique-current index)
  -> insert successor with parent reference
  -> link old to successor + update session activity
  -> commit
```

The application transaction wrapper is configured with Spring `TransactionTemplate`; domain and
application types import no Spring transaction API. The JPA repository query uses pessimistic write
locking. The partial unique index is the final database guard.

### Reuse transaction

When the locked refresh row is already used/replaced:

1. set session `COMPROMISED`, `revoked_at=now`, and reason
   `REFRESH_TOKEN_REUSE_DETECTED`;
2. set `revoked_at` and a stable reason on every unrevoked credential in the session;
3. commit before returning HTTP 401 `AUTH_REFRESH_REUSE_DETECTED`.

### Logout transactions

- Current logout locks/resolves the digest, revokes its owning session and all session credentials,
  then commits. Missing/unknown/already-revoked values are an idempotent no-op.
- Logout-all derives `user_id` only from verified JWT `sub` and bulk-revokes all active sessions and
  current credentials for that account in one transaction.
- If the transaction fails, HTTP 503 is returned while the web adapter still emits a clearing cookie.

## 4. Redis abuse-control model

Redis owns no account/session/token truth.

### Keys

```text
auth:failed:v1:{hmacIdentifier}      # sorted set; TTL 15 minutes
auth:cooldown:v1:{hmacIdentifier}    # string marker; TTL 15 minutes
```

`hmacIdentifier` is lowercase full HMAC-SHA-256 hex using a dedicated Base64 secret of at least
32 random bytes. The input is versioned and length-prefixed and contains the normalized login
identifier. Raw email/username never appears in a key, value, log, metric, trace, or baggage.

### Rolling-window operation

- On pre-check, Redis failure returns `AUTHENTICATION_UNAVAILABLE`/503 before account lookup.
- A cooldown marker returns `AUTH_TOO_MANY_ATTEMPTS`/429 and the remaining key TTL rounded up as
  `Retry-After`.
- On failed authentication, a bounded `WATCH` + `MULTI/EXEC` operation removes entries older than
  15 minutes, adds one opaque attempt member, counts the remaining entries, applies a 15-minute TTL,
  and creates the cooldown marker when count reaches five.
- Up to three immediate optimistic-contention retries are allowed. Exhaustion fails closed as 503.
- On a correct credential result, the cooldown marker is checked again immediately before creating
  the durable session so a concurrent fifth failure cannot be bypassed.
- A successful login does not erase other failures still inside the approved rolling window.

Redis restart/eviction may forget failures/cooldown; that is an accepted limitation of ephemeral
abuse state. It never creates a durable identity or session.

## 5. Retention and cleanup

- Refresh/session records remain queryable for 30 days after their logical expiry/revocation.
- A daily create-on-demand scheduling adapter invokes a cleanup input port. Its configuration explicitly
  enables Spring scheduling (`@EnableScheduling`) without leaking scheduling types into application/domain.
- Delete eligible refresh rows first when their session is retained; self references use
  `ON DELETE SET NULL`.
- Delete a session when it has been logically expired or revoked for more than 30 days; cascade then
  removes any remaining credential rows.
- A stored `ACTIVE` session whose `expires_at` passed is logically expired and eligible after the
  retention boundary. No active/unexpired session is deleted.
- Account rows are not deleted by this feature.
- Multiple service replicas may run the bounded delete safely because deletion is idempotent; no
  distributed scheduler lock is introduced for the MVP.

## 6. Migration and rollback

The initial schema migration is forward-only and additive to an empty authentication schema.
Rollback in development drops `refresh_tokens`, then `user_sessions`, then `users`. Production
rollback does not automatically drop credential data: disable new auth routes, restore the prior
application image, and retain the schema until a separately reviewed destructive migration is safe.

Migration verification must prove:

- clean apply to PostgreSQL;
- all constraints/indexes exist;
- case-insensitive normalized uniqueness races fail correctly;
- the partial unique-current index rejects two current credentials in one session;
- rollback SQL is syntactically valid for disposable test databases.
