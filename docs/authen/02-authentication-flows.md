# Authentication flows and sequence diagrams

All public calls enter through the API Gateway. The Gateway validates/rate-limits at the edge and
forwards the request to Authentication. Authentication then runs the use case through inbound and
outbound ports. The diagrams show the important classes, not every framework filter.

## 1. Registration

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as API Gateway
    participant Controller as RegistrationController
    participant Mapper as RegistrationWebMapper
    participant UseCase as RegisterAccountService
    participant Account as Account aggregate
    participant Hash as Argon2PasswordProofAdapter
    participant DB as JpaAccountPersistenceAdapter
    participant Users as PostgreSQL users

    Client->>Gateway: POST /api/v1/auth/register
    Gateway->>Controller: Forward body + X-Trace-Id
    Controller->>Controller: Validate RegisterRequest
    Controller->>Mapper: toCommand(request)
    Mapper-->>Controller: RegisterAccountCommand
    Controller->>UseCase: register(command)
    UseCase->>Account: normalize email/username
    UseCase->>DB: findByEmail/UsernameNormalized
    DB->>Users: SELECT normalized identifiers
    Users-->>DB: no duplicate
    UseCase->>Hash: encode(password)
    Hash-->>UseCase: Argon2 hash
    UseCase->>Account: register(..., ROLE_USER, ACTIVE)
    UseCase->>DB: save(account)
    DB->>Users: INSERT users row
    Users-->>DB: committed row
    DB-->>UseCase: Account
    UseCase-->>Controller: RegisterAccountResult
    Controller-->>Gateway: 201 ApiResponse + X-Trace-Id
    Gateway-->>Client: 201 response
```

The client cannot choose an administrator role. The raw password exists only in the command long
enough to be encoded; the response and database contain no raw password.

## 2. Login

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as API Gateway
    participant Controller as LoginController
    participant Throttle as RedisLoginThrottleAdapter
    participant UseCase as AuthenticateAccountService
    participant DB as JpaAuthenticationSessionPersistenceAdapter
    participant Users as PostgreSQL
    participant Password as Argon2PasswordProofAdapter
    participant Token as JwtAccessTokenAdapter
    participant Cookie as RefreshCookieWriter

    Client->>Gateway: POST /api/v1/auth/login
    Gateway->>Gateway: Apply auth-login rate policy and trace filter
    Gateway->>Controller: Forward body + headers
    Controller->>UseCase: authenticate(LoginCommand)
    UseCase->>Throttle: beforeAttempt(normalized login)
    Throttle-->>UseCase: allowed or AUTH_TOO_MANY_ATTEMPTS
    UseCase->>DB: load(normalized login)
    DB->>Users: SELECT account
    Users-->>DB: Account row or empty
    DB-->>UseCase: Account/empty
    UseCase->>Password: matches(password, stored hash)
    Password-->>UseCase: true/false
    alt invalid account or password
        UseCase->>Throttle: recordFailure(login)
        UseCase-->>Controller: generic AUTH_INVALID_CREDENTIALS
        Controller-->>Gateway: 401 error envelope
    else valid credentials
        UseCase->>Throttle: beforeCommit(login)
        UseCase->>DB: persist(account, refreshDigest, metadata)
        DB->>Users: update last login
        DB->>DB: INSERT session + current refresh row
        DB-->>UseCase: session id
        UseCase->>Token: issue login JWT
        Token-->>UseCase: RS256 access token
        UseCase-->>Controller: AuthenticationResult + raw refresh value
        Controller->>Cookie: write refresh_token HttpOnly cookie
        Controller-->>Gateway: 200 ApiResponse + X-Trace-Id + Set-Cookie
        Gateway-->>Client: 200 response
    end
```

The access token is returned in the response body. The refresh value is sent only through the
HttpOnly cookie. The database stores only its SHA-256 digest.

## 3. Refresh rotation

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as API Gateway
    participant Origin as TrustedOriginFilter
    participant Controller as RefreshController
    participant UseCase as RefreshSessionService
    participant Digest as Sha256RefreshCredentialDigestAdapter
    participant DB as JpaAuthenticationSessionPersistenceAdapter
    participant Token as JwtAccessTokenAdapter
    participant Cookie as RefreshCookieWriter
    participant PostgreSQL as PostgreSQL row lock

    Client->>Gateway: POST /api/v1/auth/refresh + refresh_token cookie
    Gateway->>Gateway: Apply auth-refresh rate policy and trace filter
    Gateway->>Origin: Check exact Origin/Referer when supplied
    Origin->>Controller: Continue if trusted
    Controller->>UseCase: refresh(raw cookie)
    UseCase->>Digest: digest(raw cookie)
    Digest-->>UseCase: token hash
    UseCase->>DB: rotate(current hash, successor hash)
    DB->>PostgreSQL: SELECT current token FOR UPDATE
    alt current, active, and unexpired
        DB->>PostgreSQL: retire current, insert successor, update session activity
        PostgreSQL-->>DB: committed rotation
        DB-->>UseCase: account/session/expiry
        UseCase->>Token: issue refreshed JWT
        Token-->>UseCase: RS256 access token
        UseCase-->>Controller: replacement token + raw cookie
        Controller->>Cookie: replace refresh_token cookie
        Controller-->>Gateway: 200 + Set-Cookie + no-store
        Gateway-->>Client: 200 response
    else already used/replayed
        DB->>PostgreSQL: mark owning session COMPROMISED and revoke chain
        DB-->>UseCase: AUTH_REFRESH_REUSE_DETECTED
        UseCase-->>Controller: 401 error; clear cookie
        Controller-->>Gateway: 401 error envelope
    end
```

The row lock is the concurrency boundary: two requests using one current cookie cannot both
successfully rotate it. A replay compromises only that session chain.

## 4. Logout current session

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as API Gateway
    participant Origin as TrustedOriginFilter
    participant Controller as LogoutController
    participant UseCase as LogoutSessionService
    participant Digest as Sha256RefreshCredentialDigestAdapter
    participant DB as JpaAuthenticationSessionPersistenceAdapter
    participant PostgreSQL as PostgreSQL
    participant Cookie as RefreshCookieWriter

    Client->>Gateway: POST /api/v1/auth/logout + cookie
    Gateway->>Origin: Check trusted Origin/Referer when supplied
    Origin->>Controller: Continue
    Controller->>UseCase: logoutCurrent(raw cookie)
    UseCase->>Digest: digest(raw cookie)
    UseCase->>DB: revokeCurrent(hash)
    DB->>PostgreSQL: revoke owning session and refresh chain
    PostgreSQL-->>DB: committed or no matching row
    DB-->>UseCase: idempotent completion
    UseCase-->>Controller: completion
    Controller->>Cookie: clear cookie
    Controller-->>Gateway: 204 empty body
    Gateway-->>Client: 204 response
```

## 5. Logout all sessions

```mermaid
sequenceDiagram
    actor Client
    participant Gateway as API Gateway
    participant Security as Gateway/Auth JWT validation
    participant Controller as LogoutAllController
    participant UseCase as LogoutSessionService
    participant DB as JpaAuthenticationSessionPersistenceAdapter
    participant PostgreSQL as PostgreSQL

    Client->>Gateway: POST /api/v1/auth/logout-all + Bearer JWT
    Gateway->>Security: Validate issuer, audience, signature, expiry
    Security-->>Gateway: verified subject and authorities
    Gateway->>Controller: Forward verified bearer token
    Controller->>Controller: Read verified Jwt.sub only
    Controller->>UseCase: logoutAll(userId)
    UseCase->>DB: revokeAll(userId)
    DB->>PostgreSQL: revoke all active sessions and their refresh chains
    PostgreSQL-->>DB: committed bulk revocation
    DB-->>UseCase: completion
    UseCase-->>Controller: completion
    Controller-->>Gateway: 204 empty body
    Gateway-->>Client: 204 response
```

## 6. Retention cleanup

```mermaid
sequenceDiagram
    participant Scheduler as ExpiredAuthenticationStateCleanupScheduler
    participant UseCase as CleanupExpiredSessionsService
    participant DB as JpaAuthenticationSessionPersistenceAdapter
    participant PostgreSQL as PostgreSQL
    participant Metrics as AuthenticationCleanupMetrics

    Scheduler->>UseCase: daily cron trigger
    UseCase->>UseCase: calculate now and retention cutoff
    UseCase->>DB: purge(now, cutoff)
    DB->>PostgreSQL: delete retained refresh rows
    DB->>PostgreSQL: delete inactive retained sessions
    PostgreSQL-->>DB: deletion counts
    DB-->>UseCase: CleanupResult
    UseCase-->>Scheduler: completion
    Scheduler->>Metrics: record run/failure counter
```

## Error ownership

```text
Domain failure (AccountFailure/SessionFailure)
  -> AuthenticationHttpExceptionHandler
  -> Auth-owned {code,message,traceId}

Redis/JPA/provider failure in an adapter
  -> adapter translates to application/domain failure
  -> HTTP handler chooses status

Gateway policy/security failure
  -> Gateway error envelope

Downstream Auth response
  -> Gateway forwards status/body/headers without changing Auth ownership
```

This keeps the Gateway responsible for edge errors while Authentication remains responsible for
account and session semantics.
