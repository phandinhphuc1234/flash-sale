# ADR 0025: One-Time Authentication Admin Bootstrap

## Status

Accepted — 2026-08-22

## Context

The cloud `auth_db` can be empty after a fresh deployment, while the approved internal E2E requires
an existing `ROLE_ADMIN`. Public registration intentionally creates only `ROLE_USER`, and the
operator must not mutate the Authentication-owned database directly from infrastructure tooling.

## Decision

Authentication owns an opt-in admin bootstrap use case and an inbound `ApplicationRunner`. A
disposable Kubernetes Job runs the already deployed Authentication image with
`AUTH_ADMIN_BOOTSTRAP_ENABLED=true`. Email, username, and password are supplied through a temporary
Kubernetes Secret created by a PowerShell launcher; the password is read as a `SecureString` and the
Secret is deleted after completion. Same-identity `ROLE_ADMIN` reruns are idempotent; collisions
with a user account or mismatched identities fail closed. The normal Authentication Deployment
keeps bootstrap disabled.

## Alternatives considered

1. Direct SQL promotion from `kubectl exec`: rejected because it bypasses Authentication ownership,
   password hashing, and domain invariants.
2. Public admin-creation endpoint: rejected because it permanently expands the external attack
   surface and is unnecessary for a one-time coursework bootstrap.
3. Fixed Liquibase seed row: rejected because it would put a shared credential or hash in migration
   history and would not support safe password rotation.

## Consequences

- A small operational Job and script are required before the Phase 22 smoke.
- The operator must manually enter and later rotate/remove the bootstrap credential.
- No database schema, public API, Kafka contract, or service boundary changes are introduced.
- The same image is used for normal Authentication and the one-time Job, preserving independent
  service ownership.
