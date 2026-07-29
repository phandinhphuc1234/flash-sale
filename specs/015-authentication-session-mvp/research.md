# Research: Authentication Session MVP

**Feature**: [015 Authentication Session MVP](spec.md)
**Date**: 2026-07-25
**Status**: Complete for planning

This artifact resolves technical choices only. Observable credential, TTL, quota, reuse,
failure-mode, and cookie behavior comes from the approved specification.

## 1. Extend the first-party service; do not add an OAuth2/OIDC server

**Decision**: Implement the approved register/login/refresh/logout HTTP contract as a custom
first-party authentication service. Reuse Spring Security's password, JWT encoder, and JWT resource
server components, but do not introduce Spring Authorization Server or OAuth2 protocol endpoints.

**Rationale**: The client and service are owned by the same project, and the approved scope excludes
authorization-code flow, consent, client registration, discovery metadata, and OIDC. Adding a full
provider would add protocol and persistence responsibilities with no MVP consumer.

**Alternatives considered**:

- Spring Authorization Server: rejected as out of scope and disproportionate for one first-party
  client.
- Server-side HTTP session for all requests: rejected because Feature 014 already establishes
  independently verified bearer access tokens.
- Calling authentication-service on every downstream request: rejected because it would place Auth
  on the flash-sale request path and conflict with local JWT verification.

## 2. Password hashing uses configurable Argon2id parameters

**Decision**: Use Spring Security `Argon2PasswordEncoder` with Argon2id, 16-byte random salt,
32-byte hash, 19,456 KiB memory, 2 iterations, and parallelism 1 as the initial configurable preset.
Retain the encoded Argon2 parameter string in `password_hash`. Add Bouncy Castle because the Spring
implementation requires it. Benchmark verification on the target VPS before deployment; raise cost
later through configuration and rehash-on-login only in a separately approved password-upgrade
change.

**Rationale**: OWASP's current minimum Argon2id preset is 19 MiB, two iterations, parallelism one.
Spring Security documents Argon2 as an adaptive memory-hard function, recommends tuning password
verification to the target system, and states that its implementation requires Bouncy Castle.

**Sources**:

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Security password storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)

**Alternatives considered**:

- Spring's older/default parameter preset without an explicit project value: rejected because it
  would hide the chosen cost and may not meet the approved deployment target.
- BCrypt: acceptable fallback in the supplied design, but rejected for new credentials because
  Argon2id is approved and available.
- Password pepper: deferred because it adds another rotating secret and recovery procedure; it is
  not required by the approved MVP.

## 3. JWT issuance reuses the verified RSA trust material

**Decision**: Extend the existing key configuration with a deployment-provided PKCS#8 RSA private
key and construct a private `RSAKey`/`JWKSource` for `NimbusJwtEncoder`. Continue publishing only
`toPublicJWK()` through the existing JWKS controller. Build a local `NimbusJwtDecoder` from the
public key for authentication-service's protected logout-all endpoint, with the same issuer,
audience, time, subject, algorithm, and authority rules as Feature 014.

**Rationale**: `NimbusJwtEncoder` is Spring Security's JWS encoder and receives signing key material
through a JWK source. A local decoder avoids an HTTP call from authentication-service to itself.
Spring Security resource server supports explicit issuer/audience validators and maps the verified
JWT into the security context.

**Sources**:

- [Spring Security `NimbusJwtEncoder`](https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html)
- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)

**Alternatives considered**:

- A second JWT library: rejected because JOSE/Nimbus is already in the module and adding another
  signer increases dependency and claim-serialization drift.
- Self-fetching JWKS: rejected because it creates unnecessary startup/runtime network coupling.
- HS256: already rejected by ADR 0005 because every verifier would need the signing secret.

## 4. Access and refresh credential formats remain deliberately different

**Decision**:

- Access token: RS256 JWS, `typ=at+jwt`, required `kid`, `iss`, `sub`, `aud`, `iat`, `exp`, `jti`,
  and `authorities`; 15-minute lifetime; not persisted.
- Refresh credential: at least 32 random bytes from `SecureRandom`, Base64 URL-safe without padding;
  only lowercase SHA-256 hex is persisted.
- Persisted roles map to the existing authority contract: `ROLE_USER` -> `ROLE_USER` and
  `ROLE_ADMIN` -> `ROLE_ADMIN`,`CATALOG_ADMIN`.

**Rationale**: A high-entropy opaque refresh value does not need password-style hashing; equality
lookup against a SHA-256 digest is appropriate because an offline attacker cannot feasibly enumerate
the random input space. Keeping access tokens stateless preserves Feature 014's local-verification
design.

**Important consequence**: Logout revokes refresh capability only. Already-issued access tokens
remain usable until their 15-minute expiry because the approved scope excludes an access-token
blacklist/introspection path.

## 5. Refresh rotation is one PostgreSQL transaction with a row lock

**Decision**: Resolve the SHA-256 digest and load its refresh row plus owning session using
`PESSIMISTIC_WRITE`/`SELECT ... FOR UPDATE` inside the transaction that decides validity, retires the
old row, inserts the successor, links both rows, and updates session activity. Do not use a Redis
distributed lock.

**Correct operation order**:

1. Lock the matching refresh row and owning session.
2. Validate session/token state and time boundaries.
3. Mark the old row used, without setting its replacement yet. This removes it from the partial
   unique-current index.
4. Insert the successor with `parent_token_id=old.id`.
5. Set `old.replaced_by_token_id=new.id` and update session activity.
6. Commit; any failure rolls the entire sequence back.

If a second transaction later locks the old row and sees it used/replaced, it atomically marks the
session compromised and revokes every refresh row in that session before returning the approved
reuse error.

**Rationale**: PostgreSQL row locks block competing writers/lockers until transaction end, and a
partial unique index can enforce one current row per session. The supplied pseudo-SQL inserted the
new current row before retiring the old one, which would violate that unique index.

**Sources**:

- [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [PostgreSQL row-level locking](https://www.postgresql.org/docs/17/explicit-locking.html)
- [PostgreSQL partial indexes](https://www.postgresql.org/docs/17/indexes-partial.html)

**Alternatives considered**:

- Redis lock: rejected because PostgreSQL owns the durable invariant and a second coordinator adds
  failure modes.
- Optimistic version/retry: rejected because strict single-use and reuse detection are clearer with
  one locked durable row.
- Delete old refresh values immediately: rejected because reuse detection requires retained history.

## 6. SameSite is defense in depth; Origin/Referer is enforced at the cookie boundary

**Decision**: Set the refresh cookie HttpOnly, SameSite=Lax, host-only, and path
`/api/v1/auth`; set Secure outside local. On refresh/current-logout, authentication-service compares
an exact normalized `Origin` with the configured allow-list, falling back to the origin portion of
`Referer` only when `Origin` is absent. Missing, `null`, malformed, wildcard/suffix-matched, or
untrusted values fail with the approved 403. Gateway forwards both headers unchanged.

**Rationale**: SameSite is scoped to site rather than origin and is not a complete CSRF control.
OWASP recommends exact Origin comparison, Referer fallback, and blocking when neither can be
validated for sensitive endpoints. Server configuration is trusted instead of proxy-derived host
headers.

**Source**: [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

**Alternatives considered**:

- SameSite=Lax alone: rejected by the approved Q3 decision.
- SameSite=Strict: rejected because Q3 preserves Lax usability.
- CSRF synchronizer/double-submit token: valid but rejected for this MVP by Q3; Origin enforcement
  is smaller for the two JSON cookie endpoints.

## 7. Redis account throttle uses bounded transactions, not a new Lua script

**Decision**: Use Spring Data Redis operations behind an application output port. Store only keyed
HMAC-SHA-256 identifier digests. Model the 15-minute failure window as a sorted set of opaque attempt
IDs/timestamps and cooldown as a separate expiring key. Use `WATCH` plus `MULTI/EXEC` with a bounded
three-attempt contention retry to prune, add, count, and create cooldown without lost updates. A
correct-password path rechecks cooldown immediately before durable session creation. Exhausted
transaction retries and Redis errors fail closed as HTTP 503.

**Rationale**: This preserves the approved rolling window and fifth-failure boundary without making
Redis authoritative. It also avoids adding another Lua script where repository guidance reserves Lua
for separately approved atomic hot paths. Redis state expires and can be reconstructed from future
attempts; it is not reconciled into PostgreSQL.

**Source**: [Spring Data Redis reference](https://docs.spring.io/spring-data/redis/reference/redis.html)

**Alternatives considered**:

- Simple `INCR` plus `EXPIRE`: rejected because expiry races and reset semantics do not accurately
  represent the approved rolling window under concurrency.
- New Redis Lua script: rejected to avoid expanding the repository's Lua ownership policy.
- Persisting failed attempts in PostgreSQL: rejected because high-churn temporary abuse state is the
  approved Redis responsibility.

## 8. Gateway extends existing route-scoped policies rather than adding another limiter

**Decision**: Add exact POST route IDs for login and refresh ahead of the general auth route, then
generalize `GatewayRateLimitProperties` from one hard-coded catalog preset to the three approved
presets. Reuse the existing token-bucket coordinator, HMAC identity, 50 ms timeout,
`RATE_LIMIT_EXCEEDED` response, `Retry-After`, and fail-open metric behavior. Preserve direct socket
IP semantics from ADR 0004.

**Rationale**: The existing resolver selects by exact route ID and method, so distinct route IDs are
the smallest way to give login and refresh different quotas. A second rate-limiter stack would
duplicate keys, errors, metrics, and Redis behavior.

**Operational limitation**: Behind an ingress/proxy, direct peer IP can collapse callers into the
proxy identity. Feature 015 does not authorize forwarded-header trust; production IP limiting still
requires the future trusted-proxy decision recorded by ADR 0004.

## 9. HTTP ownership and response shape follow existing repository contracts

**Decision**:

- Successful non-204 auth responses use `{data,traceId}`; access/refresh values appear only in the
  approved token result/cookie locations.
- Auth-owned errors use `{code,message,traceId}`.
- Gateway-owned errors keep Feature 011/013's `{code,message,traceId}` contract.
- Gateway passes downstream auth status, headers, and body through unchanged.
- Login/refresh responses add `Cache-Control: no-store`; logout responses are empty 204; 429 adds an
  integer `Retry-After`.

**Rationale**: This retains the repository's existing error/correlation terminology and avoids
introducing the attachment's parallel `meta.requestId`. `common-web` is not used because its current
success/error records do not contain the verified `traceId` contract and changing that shared module
would create unrelated cross-service compatibility work.

## 10. Clean/Hexagonal structure is organized by capability

**Decision**: Keep account and session invariants in Java-only domain packages; group application
commands/results/ports/use cases by registration, login, refresh, and logout capability; keep HTTP,
JPA, Redis, password/JWT, and scheduling details in their owning adapters. Use `TransactionTemplate`
in configuration to wrap pure application mutation services, following the established Product
Service pattern.

**Rationale**: This retains `adapter -> application -> domain` and prevents a global `dto`, `mapper`,
`exception`, or `repository` dumping ground. Atomic transaction ownership remains in Spring
configuration without importing Spring transactions into the application core.

**Alternatives considered**:

- The attachment's `domain/repository` plus generic `infrastructure` tree: refined because repository
  interfaces are application output ports here and technology-specific types belong in adapters.
- One large `AuthenticationService`: rejected because registration, login, refresh, and logout have
  different invariants/failure paths.
- Creating every baseline subpackage immediately: rejected by the repository create-on-demand rule.
