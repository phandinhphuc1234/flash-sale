# ADR 0006: Authentication Session and Refresh Rotation Boundary

**Status**: Accepted

**Date**: 2026-07-25

**Feature**: [015 Authentication Session MVP](../../specs/015-authentication-session-mvp/spec.md)

## Context

ADR 0005 established an RS256/JWKS trust boundary but deliberately excluded credentials, token
issuance, refresh, logout, and revocation. Feature 015 now needs a first-party browser authentication
capability without turning authentication-service into a full OAuth2/OIDC provider or placing it on
every downstream request path.

The design must answer four architectural questions:

1. where accounts, sessions, and refresh state are durably owned;
2. how single-use refresh rotation and concurrent replay remain correct;
3. how a browser holds a long-lived credential without exposing it to JavaScript or CSRF;
4. how Gateway IP controls and authentication-service account controls share the existing Redis
   runtime without making Redis identity truth.

## Decision

### Service and protocol boundary

- `authentication-service` owns accounts, password hashes, login sessions, refresh credential
  digests, issuance, rotation, reuse detection, and revocation.
- Public auth traffic enters through api-gateway at `/api/v1/auth/**`.
- This remains a custom first-party JSON/cookie API. It is not a Spring Authorization Server,
  OAuth2 Authorization Server, or OpenID Connect Provider.
- After login, Gateway and downstream services verify access tokens locally under ADR 0005; Auth is
  not called on every business request.

### Durable and ephemeral data

- Authentication PostgreSQL (`auth_db`) is the durable source of truth for `users`, `user_sessions`,
  and hashed `refresh_tokens`.
- Raw refresh values are opaque random client credentials. PostgreSQL stores only SHA-256 digests.
- The existing root-owned Redis instance stores only expiring, keyed-HMAC account failure/cooldown
  state. Redis loss cannot create an account, session, token, or authority.
- No Kafka event or outbox is introduced because Phase 1 publishes no integration event.

### Access and refresh token model

- Access tokens are stateless RS256 JWS values with 15-minute lifetime and the exact ADR 0005
  issuer/audience/`kid`/authority contract.
- Refresh credentials have seven-day inactivity lifetime within a 30-day absolute session lifetime.
- One session can have at most one current refresh credential.
- A PostgreSQL pessimistic row lock and partial unique index protect rotation. The transaction retires
  the old credential before inserting the successor so the unique-current invariant is never
  temporarily violated.
- Reuse of an already-rotated credential marks only its session `COMPROMISED` and revokes that
  session's refresh chain before returning the public reuse error.
- No Redis distributed lock is used for refresh correctness.

### Browser cookie and CSRF boundary

- The raw refresh credential uses a host-only, HttpOnly, path-scoped, SameSite=Lax cookie; Secure is
  mandatory outside the explicit local profile.
- Refresh and current logout require an exact configured Origin match, with a parsed same-origin
  Referer fallback only when Origin is absent. Missing/untrusted values fail closed with HTTP 403.
- Gateway forwards Origin/Referer and Set-Cookie unchanged; authentication-service owns this check
  because it owns the cookie-backed session mutation.
- Access tokens remain in the response body and are expected to be held in client memory.

### Abuse controls

- Gateway reuses ADR 0004's distributed token bucket for login 10/min/direct-IP and refresh
  30/min/direct-IP. Coordinator failure follows the approved fail-open-with-metric behavior.
- Authentication-service independently enforces five failed logins per normalized-identifier HMAC
  in a rolling 15-minute window followed by a 15-minute cooldown.
- Authentication account-throttle Redis failure fails closed with HTTP 503.
- Redis transactions/optimistic locking are used for this bounded state; no new authentication Lua
  script is introduced.

### Architecture

- Authentication business types follow `adapter -> application -> domain` with configuration wiring
  adapters/application.
- Web DTO/status/cookie behavior, JPA entities, Redis keys, and Spring Security/Nimbus types remain in
  adapters/configuration and do not leak into domain/application models.
- Gateway retains ADR 0003's lean edge packages; only routes, security rules, trace propagation, and
  existing rate-limit policy configuration are extended.

## Alternatives considered

### Full OAuth2/OIDC provider

Rejected because Phase 1 has one first-party client and does not require authorization code,
consent, discovery, client registration, or ID tokens.

### Store refresh credentials as JWTs

Rejected because server-side session revocation/reuse detection still needs durable state, while an
opaque random value reduces exposed claims and simplifies equality lookup by digest.

### Store raw refresh credentials or Argon2-hash them

Raw storage is rejected because a database disclosure would immediately expose usable credentials.
Argon2 is unnecessary for high-entropy random values and prevents indexed equality lookup; SHA-256
digest storage is selected.

### Redis as session/token store

Rejected because Redis is ephemeral under the repository constitution. Restart/eviction must not
erase durable revocation or recreate validity.

### Redis lock or Lua for refresh rotation

Rejected because PostgreSQL already owns both the rows and invariant. A second coordinator would add
split-brain/failure recovery, while a row lock plus unique index closes the concurrency boundary.

### SameSite cookie without Origin checking

Rejected by the approved security decision because SameSite is site-scoped and defense in depth, not
the sole cookie-authenticated mutation control.

### Access-token blacklist

Rejected from the MVP because it would add an online lookup to every protected request or another
distributed cache contract. Logout revokes refresh ability; a previously issued access token may
remain usable for at most 15 minutes.

## Consequences

### Positive

- Downstream requests remain independent of Auth availability after token issuance.
- A database leak does not directly reveal raw passwords or refresh credentials.
- Rotation/reuse behavior remains deterministic under concurrent requests and multiple replicas.
- One device can be revoked/compromised without ending unrelated sessions; logout-all remains
  available when the user needs global revocation.
- Gateway and Auth abuse controls cover different identities and failure modes without duplicate
  limiter implementations.
- The service can evolve by capability without a global DTO/mapper/exception/repository package.

### Negative and accepted trade-offs

- Strict reuse detection can compromise a legitimate session after a client retries an old refresh
  value whose first response was lost. The client contract requires re-login after ambiguous refresh.
- Already-issued access tokens survive logout/account state changes until their 15-minute expiry.
- Redis outage blocks login at the account-throttle boundary but does not invalidate durable sessions.
- Direct socket IP at Gateway may represent an ingress/proxy in clustered deployment; trusted
  forwarded-client identity remains a separate ADR 0004 follow-up.
- Origin allow-lists require correct environment configuration and must be updated with frontend
  deployment origins.
- Argon2id consumes deliberate CPU/memory and must be benchmarked on the target VPS.

## Migration and rollout impact

1. Add the authentication schema through the service-owned Liquibase changelog.
2. Add service dependencies/configuration and deployment-provided database, Redis, HMAC, RSA private
   key, cookie, and trusted-origin values.
3. Add exact Gateway auth routes/security/rate-limit policies while preserving existing routes.
4. Run module, migration, concurrency, contract, secret-leak, and local Compose checks before
   enabling public auth endpoints.
5. Do not introduce Kubernetes resources in this feature; a future Kubernetes deployment feature
   must mount keys/secrets and validate overlays.

## Rollback

- Disable/remove the three Gateway auth routes to stop new public auth traffic.
- Restore the previous authentication-service image; ADR 0005 JWKS publication remains usable.
- Do not drop `users`, `user_sessions`, or `refresh_tokens` automatically. Retain them for a forward
  fix or use a separately reviewed destructive migration only in disposable environments.
- Gateway login/refresh rate-limit keys and Auth failure/cooldown keys expire naturally; no Redis
  flush is required.

## Future decision boundaries

Separate approved features/ADRs are required for administrator provisioning, password recovery or
change, email verification, MFA, security events/Kafka outbox, access-token revocation,
automated/multi-key rotation, trusted proxy identity, native-mobile refresh transport, and production
Kubernetes secret/Redis topology.
