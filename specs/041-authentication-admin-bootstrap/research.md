# Research: One-Time Authentication Admin Bootstrap

## Decision 1 — Provision through the Authentication application, not SQL

**Decision**: Add an opt-in application use case and an `ApplicationRunner` adapter to the existing
Authentication image. A Kubernetes Job runs that image once.

**Rationale**: Authentication owns `auth_db`, password hashing, account invariants, and the
`ROLE_ADMIN` authority mapping. Direct SQL from `infra/` would bypass those boundaries and could
create a hash or lifecycle state the service does not accept.

**Alternatives rejected**: `kubectl exec`/SQL promotion (cross-boundary mutation); a public admin
creation endpoint (unnecessary permanent attack surface); a fixed Liquibase row (hard-coded shared
credential and unsafe password lifecycle).

## Decision 2 — Bootstrap is disabled by default and Job-scoped

**Decision**: `AUTH_ADMIN_BOOTSTRAP_ENABLED` defaults to `false`. The launcher applies a disposable
Job with the flag set to `true`, then removes its Secret and Job.

**Rationale**: A normal Deployment restart must never recreate or rotate an admin. A Job gives the
operator an explicit, auditable execution boundary and keeps credentials out of Git desired state.

## Decision 3 — Idempotent same-identity success, fail-closed collisions

**Decision**: A matching existing `ROLE_ADMIN` is a success without password replacement. A
`ROLE_USER` collision or email/username mismatch fails without mutation.

**Rationale**: Retry safety is needed after a timeout, while silent privilege escalation is not
acceptable for an authentication bootstrap.

## Decision 4 — Reuse the existing password policy and Argon2 adapter

**Decision**: The bootstrap application validates the existing 12–128 code-point policy and reuses
`EncodePasswordPort`, whose adapter is the configured Argon2id implementation.

**Rationale**: This avoids a second password policy or hashing implementation and keeps provider
details outside the application core.
