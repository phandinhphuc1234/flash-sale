# Authentication class responsibilities

The tables below are the class-level comments for the current Authentication service. Each class
has one primary responsibility. If a class starts taking a second responsibility, create or move
that responsibility to the appropriate capability package instead of growing a global utility or
exception package.

## Bootstrap and configuration

| Type | Responsibility | Collaborates with |
|---|---|---|
| `AuthenticationServiceApplication` | Spring Boot entry point; starts the service. | Spring Boot |
| `AuthenticationApplicationConfiguration` | Wires application use cases to discovered ports and adapters. | Application ports, security adapters, `Clock` |
| `AuthenticationProperties` | Binds cookie, origin, throttle, retention, and cleanup settings. | `application.yml` |
| `AuthenticationSchedulingConfiguration` | Enables scheduling and creates the cleanup use case when persistence is available. | Cleanup use case, properties |
| `AuthenticationSecurityConfiguration` | Defines stateless HTTP security and public/protected endpoint rules. | `JwtDecoder`, Spring Security |
| `JwtTrustProperties` | Binds issuer, audience, key id, and public-key settings. | JWT configuration |
| `JwtPublicKeyConfiguration` | Loads the public RSA key and publishes the public JWKS decoder. | `JwtTrustProperties`, Nimbus |
| `JwtSigningConfiguration` | Loads the private RSA key and creates local JWT encoder/decoder beans. | `JwtTrustProperties`, Nimbus |

## Domain: account aggregate

| Type | Responsibility |
|---|---|
| `Account` | Owns account identity, normalization, role/status, and authentication eligibility. It is Java-only. |
| `AccountRole` | Enumerates account authorities (`ROLE_USER`, `ROLE_ADMIN`). |
| `AccountStatus` | Enumerates account lifecycle states. |
| `AccountFailure` | Expresses an account-domain failure without HTTP or Spring types. |

## Domain: session aggregate

| Type | Responsibility |
|---|---|
| `LoginSession` | Owns session activity, expiry, revocation, and compromise state. |
| `LoginSessionStatus` | Enumerates active, revoked, and compromised session states. |
| `RefreshCredential` | Represents a hashed refresh-token-chain entry and current/replayed checks. |
| `SessionFailure` | Expresses refresh/session lifecycle failures without HTTP or persistence types. |

## Application: registration

| Type | Responsibility | Direction |
|---|---|---|
| `RegisterAccountCommand` | Input data for registration; no HTTP annotations. | Input model |
| `RegisterAccountResult` | Safe registration result containing no password. | Output model |
| `RegisterAccountUseCase` | Input port for registering an account. | Inbound port |
| `RegisterAccountPort` | Output port for account lookup and durable save. | Outbound port |
| `EncodePasswordPort` | Output capability for password hashing. | Outbound port |
| `RegisterAccountService` | Normalizes identifiers, validates password length, forces `ROLE_USER/ACTIVE`, and orchestrates ports. | Use case |

## Application: login

| Type | Responsibility | Direction |
|---|---|---|
| `AuthenticateAccountCommand` | Login input plus bounded device and peer metadata. | Input model |
| `AuthenticationResult` | Safe access-token and refresh-cookie data returned by the use case. | Output model |
| `AuthenticateAccountUseCase` | Input port for login. | Inbound port |
| `LoadAccountForAuthenticationPort` | Loads an account by normalized identifier. | Outbound port |
| `PasswordProofPort` | Checks a supplied password against a stored hash. | Outbound port |
| `LoginThrottlePort` | Checks and records login-attempt throttling. | Outbound port |
| `PersistSuccessfulLoginPort` | Atomically stores account login metadata, session, and current refresh digest. | Outbound port |
| `IssueLoginCredentialsPort` | Issues the access-token portion of a successful login. | Outbound port |
| `LoginThrottleUnavailableException` | Typed application failure when the throttle dependency is unavailable. | Application failure |
| `AuthenticateAccountService` | Coordinates throttle checks, account lookup, password proof, durable success, and token issuance. | Use case |

## Application: refresh

| Type | Responsibility | Direction |
|---|---|---|
| `RefreshSessionCommand` | Carries the raw refresh cookie into the use case. | Input model |
| `RefreshSessionResult` | Carries the new access token and replacement cookie data. | Output model |
| `RefreshSessionUseCase` | Input port for refresh rotation. | Inbound port |
| `RotateRefreshCredentialPort` | Performs locked, durable current-token rotation and reuse detection. | Outbound port |
| `IssueRefreshedCredentialsPort` | Issues the replacement access token after committed rotation. | Outbound port |
| `RefreshSessionService` | Hashes the cookie, requests rotation, and issues credentials only after persistence succeeds. | Use case |

## Application: logout

| Type | Responsibility | Direction |
|---|---|---|
| `LogoutCurrentSessionCommand` | Carries the current raw refresh cookie. | Input model |
| `LogoutAllSessionsCommand` | Carries the verified JWT subject UUID. | Input model |
| `LogoutSessionUseCase` | Input port for current-session logout. | Inbound port |
| `LogoutAllSessionsUseCase` | Input port for subject-owned logout-all. | Inbound port |
| `RevokeAuthenticationSessionsPort` | Revokes one session chain or all sessions for a user. | Outbound port |
| `LogoutSessionService` | Hashes the current cookie or accepts the already verified subject and delegates revocation. | Use case |

## Application: cleanup

| Type | Responsibility | Direction |
|---|---|---|
| `CleanupExpiredSessionsUseCase` | Input port for retention cleanup. | Inbound port |
| `PurgeExpiredAuthenticationStatePort` | Output port for deleting retained refresh/session rows. | Outbound port |
| `CleanupExpiredSessionsService` | Calculates the clock-based retention cutoff and invokes the purge port. | Use case |

## Inbound web adapters

| Type | Responsibility |
|---|---|
| `JwksController` | Publishes the public-only JWKS document used by trusted consumers. |
| `RegistrationController` | Maps HTTP registration requests to the registration input port and wraps the response. |
| `RegisterRequest` | Validates and captures the public registration body, including rejected privilege fields. |
| `RegisterResponse` | Safe HTTP registration response DTO. |
| `RegistrationWebMapper` | Converts registration HTTP DTOs to application command/result types. |
| `LoginController` | Captures bounded request metadata, invokes login, and writes the refresh cookie. |
| `LoginRequest` | Validates login HTTP input. |
| `TokenResponse` | Safe HTTP access-token response DTO. |
| `LoginWebMapper` | Converts login HTTP DTOs and application results. |
| `RefreshController` | Reads the refresh cookie, invokes rotation, and writes its replacement. |
| `RefreshCookieWriter` | Centralizes cookie attributes and cookie clearing. |
| `LogoutController` | Performs idempotent current-session logout and clears the cookie. |
| `LogoutAllController` | Extracts the already verified JWT subject and invokes logout-all. |
| `AuthenticationApiResponse` | Success envelope containing `data` and `traceId`. |
| `AuthenticationErrorResponse` | Error envelope containing `code`, `message`, and `traceId`. |
| `AuthenticationErrorCode` | Stable Auth-owned HTTP error taxonomy. |
| `AuthenticationHttpExceptionHandler` | Translates domain/application/web failures into safe HTTP statuses and bodies. |
| `AuthenticationRequestContext` | Stores the request trace-id attribute key. |
| `AuthenticationTraceFilter` | Accepts or creates a trace id, echoes it in the response, and cleans request state. |
| `AuthenticationHttpMetrics` | Records bounded HTTP operation/status timers at the adapter boundary. |
| `TrustedOriginFilter` | Enforces exact trusted Origin/Referer checks for cookie-backed commands. |

## Inbound scheduling adapter

| Type | Responsibility |
|---|---|
| `ExpiredAuthenticationStateCleanupScheduler` | Triggers the cleanup input port on the configured cron schedule. |
| `AuthenticationCleanupMetrics` | Records bounded cleanup-run and cleanup-failure counters. |

## Outbound persistence adapters

| Type | Responsibility |
|---|---|
| `AccountJpaEntity` | JPA representation of the service-owned `users` row. |
| `AccountJpaRepository` | Spring Data access for account rows. |
| `AccountPersistenceMapper` | Converts between `Account` and `AccountJpaEntity`. |
| `JpaAccountPersistenceAdapter` | Implements registration persistence using the account repository. |
| `LoginSessionJpaEntity` | JPA representation of `user_sessions`. |
| `RefreshCredentialJpaEntity` | JPA representation of `refresh_tokens`. |
| `LoginSessionJpaRepository` | Session queries, subject revocation, and inactive-row cleanup. |
| `RefreshCredentialJpaRepository` | Locked current-token lookup, chain lookup, and token cleanup. |
| `SessionPersistenceMapper` | Converts session persistence rows to domain models when needed. |
| `JpaAuthenticationSessionPersistenceAdapter` | Implements account loading, successful-login persistence, refresh rotation, logout, and cleanup ports. |

## Outbound Redis adapters

| Type | Responsibility |
|---|---|
| `LoginThrottleKeyFactory` | Creates HMAC-derived Redis keys without storing raw identifiers. |
| `RedisLoginThrottleAdapter` | Implements cooldown and rolling-window failure tracking with bounded `WATCH/MULTI/EXEC` retries. |
| `LoginThrottleMetrics` | Records throttle failures and dependency-unavailable counters without identifier tags. |

## Outbound security adapters

| Type | Responsibility |
|---|---|
| `Argon2PasswordProofAdapter` | Encodes and verifies passwords using Spring Security Argon2 at the adapter boundary. |
| `JwtAccessTokenAdapter` | Creates RS256 access tokens with issuer, audience, subject, authority, and timing claims. |
| `SecureRefreshCredentialAdapter` | Generates opaque cryptographically secure refresh values. |
| `Sha256RefreshCredentialDigestAdapter` | Converts raw refresh values to fixed-length storage digests. |

## How to read a class dependency

For example, `LoginController` may depend on `AuthenticateAccountUseCase`, but it must not depend
on `AccountJpaRepository` or `StringRedisTemplate`. `AuthenticateAccountService` may depend on
application ports, but it must not import Spring Security or JPA. The concrete adapters implement
those ports and are connected only in `AuthenticationApplicationConfiguration`.
