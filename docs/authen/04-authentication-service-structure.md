# Authentication service structure

This document is the complete structural map of the current Authentication service. It shows
where each responsibility lives and how the folders follow DDD, Clean Architecture, and Hexagonal
Architecture.

## 1. Repository-level view

```text
flash-sale/
├── services/
│   └── authentication-service/
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/com/philia/flashsale/authentication/
│           │   └── resources/
│           │       ├── application.yml
│           │       └── db/changelog/
│           └── test/java/com/philia/flashsale/authentication/
│
├── docs/
│   └── authen/
│       ├── README.md
│       ├── 01-class-responsibilities.md
│       ├── 02-authentication-flows.md
│       ├── 03-boundary-rules.md
│       └── 04-authentication-service-structure.md
│
└── infra/docker/
    ├── compose.yml
    ├── .env.example
    └── README.md
```

`services/authentication-service` owns its source code, runtime configuration, tests, and database
migrations. Root `infra/` owns shared runtime orchestration; it does not own Authentication's
business schema.

## 2. Original Feature 015 layer-first tree (historical)

> The tree in this section records the layout used when Feature 015 was initially implemented. The current source tree is feature-oriented; use the feature-first tree documented later in this file as the authoritative layout.

```text
com/philia/flashsale/authentication/
├── AuthenticationServiceApplication.java
│
├── configuration/
│   ├── AuthenticationApplicationConfiguration.java
│   ├── AuthenticationProperties.java
│   ├── AuthenticationSchedulingConfiguration.java
│   ├── AuthenticationSecurityConfiguration.java
│   ├── JwtTrustProperties.java
│   ├── JwtPublicKeyConfiguration.java
│   └── JwtSigningConfiguration.java
│
├── domain/
│   ├── account/
│   │   ├── Account.java
│   │   ├── AccountFailure.java
│   │   ├── AccountRole.java
│   │   └── AccountStatus.java
│   │
│   └── session/
│       ├── LoginSession.java
│       ├── LoginSessionStatus.java
│       ├── RefreshCredential.java
│       └── SessionFailure.java
│
├── application/
│   ├── registration/
│   │   ├── EncodePasswordPort.java
│   │   ├── RegisterAccountCommand.java
│   │   ├── RegisterAccountPort.java
│   │   ├── RegisterAccountResult.java
│   │   ├── RegisterAccountService.java
│   │   └── RegisterAccountUseCase.java
│   │
│   ├── login/
│   │   ├── AuthenticateAccountCommand.java
│   │   ├── AuthenticateAccountService.java
│   │   ├── AuthenticateAccountUseCase.java
│   │   ├── AuthenticationResult.java
│   │   ├── IssueLoginCredentialsPort.java
│   │   ├── LoadAccountForAuthenticationPort.java
│   │   ├── LoginThrottlePort.java
│   │   ├── LoginThrottleUnavailableException.java
│   │   ├── PasswordProofPort.java
│   │   └── PersistSuccessfulLoginPort.java
│   │
│   ├── refresh/
│   │   ├── IssueRefreshedCredentialsPort.java
│   │   ├── RefreshSessionCommand.java
│   │   ├── RefreshSessionResult.java
│   │   ├── RefreshSessionService.java
│   │   ├── RefreshSessionUseCase.java
│   │   └── RotateRefreshCredentialPort.java
│   │
│   ├── logout/
│   │   ├── LogoutAllSessionsCommand.java
│   │   ├── LogoutAllSessionsUseCase.java
│   │   ├── LogoutCurrentSessionCommand.java
│   │   ├── LogoutSessionService.java
│   │   ├── LogoutSessionUseCase.java
│   │   └── RevokeAuthenticationSessionsPort.java
│   │
│   └── cleanup/
│       ├── CleanupExpiredSessionsService.java
│       ├── CleanupExpiredSessionsUseCase.java
│       └── PurgeExpiredAuthenticationStatePort.java
│
└── adapter/
    ├── in/
    │   ├── web/
    │   │   ├── JwksController.java
    │   │   │
    │   │   ├── security/
    │   │   │   └── TrustedOriginFilter.java
    │   │   │
    │   │   └── auth/
    │   │       ├── AuthenticationApiResponse.java
    │   │       ├── AuthenticationErrorCode.java
    │   │       ├── AuthenticationErrorResponse.java
    │   │       ├── AuthenticationHttpExceptionHandler.java
    │   │       ├── AuthenticationHttpMetrics.java
    │   │       ├── AuthenticationRequestContext.java
    │   │       ├── AuthenticationTraceFilter.java
    │   │       ├── LoginController.java
    │   │       ├── LoginRequest.java
    │   │       ├── LoginWebMapper.java
    │   │       ├── LogoutAllController.java
    │   │       ├── LogoutController.java
    │   │       ├── RefreshController.java
    │   │       ├── RefreshCookieWriter.java
    │   │       ├── RegisterRequest.java
    │   │       ├── RegisterResponse.java
    │   │       ├── RegistrationController.java
    │   │       ├── RegistrationWebMapper.java
    │   │       └── TokenResponse.java
    │   │
    │   └── scheduling/
    │       ├── AuthenticationCleanupMetrics.java
    │       └── ExpiredAuthenticationStateCleanupScheduler.java
    │
    └── out/
        ├── persistence/
        │   ├── account/
        │   │   ├── AccountJpaEntity.java
        │   │   ├── AccountJpaRepository.java
        │   │   ├── AccountPersistenceMapper.java
        │   │   └── JpaAccountPersistenceAdapter.java
        │   │
        │   └── session/
        │       ├── JpaAuthenticationSessionPersistenceAdapter.java
        │       ├── LoginSessionJpaEntity.java
        │       ├── LoginSessionJpaRepository.java
        │       ├── RefreshCredentialJpaEntity.java
        │       ├── RefreshCredentialJpaRepository.java
        │       └── SessionPersistenceMapper.java
        │
        ├── redis/
        │   ├── LoginThrottleKeyFactory.java
        │   ├── LoginThrottleMetrics.java
        │   └── RedisLoginThrottleAdapter.java
        │
        └── security/
            ├── Argon2PasswordProofAdapter.java
            ├── JwtAccessTokenAdapter.java
            ├── SecureRefreshCredentialAdapter.java
            └── Sha256RefreshCredentialDigestAdapter.java
```

## 3. Resources and migrations

```text
services/authentication-service/src/main/resources/
├── application.yml
│   ├── Spring Boot/runtime settings
│   ├── PostgreSQL datasource
│   ├── Redis connection
│   ├── JWT issuer/audience/key locations
│   ├── cookie and trusted-origin policy
│   ├── login throttle policy
│   ├── retention scheduler policy
│   └── Actuator health/readiness/Prometheus exposure
│
└── db/changelog/
    ├── db.changelog-master.yaml
    └── changes/
        └── 001-create-authentication-schema.sql
            ├── users
            ├── user_sessions
            └── refresh_tokens
```

The migration belongs to Authentication because PostgreSQL schema ownership belongs to the
service. The Docker Compose file may create the `auth_db` database, but Liquibase creates and
evolves the service tables.

## 4. Test tree

Tests follow the same feature-first package names as production code. The following list describes
the logical groups; older layer-first names in the historical examples should not be recreated.

Authoritative current test locations:

```text
authentication/
├── account/{domain,application/registration}
├── session/domain
├── security/jwks
└── AuthenticationServiceApplicationTests.java
```

```text
services/authentication-service/src/test/java/com/philia/flashsale/authentication/
├── AuthenticationServiceApplicationTests.java
│
├── domain/
│   ├── account/AccountTests.java
│   └── session/LoginSessionTests.java
│
├── application/
│   └── registration/RegisterAccountServiceTests.java
│
├── adapter/
│   └── in/web/
│       └── JwksControllerTests.java
│
├── adapter/out/persistence/
│   └── (PostgreSQL/Testcontainers integration tests planned)
│
├── adapter/out/redis/
│   └── (Redis contention/failure tests planned)
│
└── integration/
    └── (cross-story Authentication scenarios planned)
```

Tests are placed beside the boundary they protect:

- `domain` tests do not start Spring.
- `application` tests use fake ports.
- adapter tests verify JPA, Redis, JWT, HTTP, and cookie behavior.
- integration tests verify the complete service path.

## 5. Dependency direction

```text
                    configuration
                   /             \
                  v               v
            adapter/in       adapter/out
                  |               |
                  v               v
             application  <---- ports
                  |
                  v
                domain
```

More precisely:

```text
adapter/in  -> application -> domain
adapter/out -> application -> domain
configuration -> adapter + application
domain -> Java standard library only
```

Examples:

```text
RegistrationController
  -> RegisterAccountUseCase
  -> RegisterAccountService
  -> RegisterAccountPort / EncodePasswordPort
  -> JpaAccountPersistenceAdapter / Argon2PasswordProofAdapter
```

```text
RefreshController
  -> RefreshSessionUseCase
  -> RefreshSessionService
  -> RotateRefreshCredentialPort / IssueRefreshedCredentialsPort
  -> JpaAuthenticationSessionPersistenceAdapter / JwtAccessTokenAdapter
```

The controller does not call JPA directly, and the application service does not call Redis or
Nimbus directly. `AuthenticationApplicationConfiguration` connects the interfaces to concrete
Spring beans.

## 6. Where future classes should go

| New responsibility | Location |
|---|---|
| Account/session invariant | `domain/account` or `domain/session` |
| New use-case orchestration | `application/<capability>` |
| New use-case input/output port | `application/<capability>` |
| New REST endpoint or DTO | `<feature>/adapter/in/web` |
| New scheduled trigger | `cleanup/adapter/in/scheduling` (or the owning feature) |
| New database entity/repository/mapper | `<feature>/adapter/out/persistence` |
| New Redis behavior | `throttle/redis` (or the owning feature's outbound adapter) |
| New password/JWT provider | `security/password` or `security/token` |
| Spring bean wiring or properties | `configuration` |

Do not create a global `dto`, `model`, `mapper`, `exception`, or `utils` package merely because
the number of classes increases. Split by capability or aggregate first.

## 7. Current feature-first tree after T071

Feature 015 now uses the feature-first layout below. Developers enter `account` or `session`
before entering a technical layer, while cross-cutting capabilities remain grouped separately.

```text
com/philia/flashsale/authentication/
├── account/
│   ├── domain/
│   │   ├── Account.java
│   │   ├── AccountRole.java
│   │   └── AccountStatus.java
│   ├── application/
│   │   └── registration/
│   │       ├── RegisterAccountUseCase.java
│   │       ├── RegisterAccountService.java
│   │       ├── RegisterAccountCommand.java
│   │       ├── RegisterAccountResult.java
│   │       └── ports/
│   └── adapter/
│       ├── in/web/
│       │   ├── RegistrationController.java
│       │   ├── RegisterRequest.java
│       │   └── RegisterResponse.java
│       └── out/persistence/
│           ├── AccountJpaEntity.java
│           ├── AccountJpaRepository.java
│           ├── AccountPersistenceMapper.java
│           └── JpaAccountPersistenceAdapter.java
│
├── session/
│   ├── domain/
│   │   ├── LoginSession.java
│   │   ├── LoginSessionStatus.java
│   │   ├── RefreshCredential.java
│   │   └── SessionFailure.java
│   ├── application/
│   │   ├── login/
│   │   ├── refresh/
│   │   └── logout/
│   └── adapter/
│       ├── in/web/
│       │   ├── LoginController.java
│       │   ├── RefreshController.java
│       │   ├── LogoutController.java
│       │   └── LogoutAllController.java
│       └── out/persistence/
│           ├── LoginSessionJpaEntity.java
│           ├── RefreshCredentialJpaEntity.java
│           ├── LoginSessionJpaRepository.java
│           ├── RefreshCredentialJpaRepository.java
│           └── JpaAuthenticationSessionPersistenceAdapter.java
│
├── security/
│   ├── password/
│   │   └── Argon2PasswordProofAdapter.java
│   ├── token/
│   │   ├── JwtAccessTokenAdapter.java
│   │   ├── SecureRefreshCredentialAdapter.java
│   │   └── Sha256RefreshCredentialDigestAdapter.java
│   └── jwks/
│       └── JwksController.java
│
├── throttle/
│   └── redis/
│       ├── LoginThrottleKeyFactory.java
│       └── RedisLoginThrottleAdapter.java
├── cleanup/
│   ├── application/
│   └── adapter/in/scheduling/
├── websupport/
│   ├── error/
│   ├── filter/
│   └── context/
├── observability/
└── configuration/
```

This target intentionally keeps the useful idea from package-by-feature without copying every
possible folder from a template:

- `account` and `session` expose the business language first.
- `security`, `throttle`, and `cleanup` remain technical capabilities because they support more
  than one endpoint or have their own operational policy.
- HTTP DTOs stay beside the feature endpoint that owns them.
- JPA types stay beside the persistence adapter for the owning feature.
- Ports remain inside the feature's application boundary; they are not low-level technology ports.

The T071 refactor only changed package ownership and imports. HTTP/JWT behavior, persistence schema,
contracts, and dependencies remain governed by Feature 015 and ADR 0007. New capabilities should
follow this layout and must still preserve `adapter -> application -> domain` dependency direction.

The previous layer-first Java locations and empty scaffold markers are not part of the active source
tree. A Git diff may therefore show old files as deleted and new package paths as added; this is the
expected representation of a mechanical move, not a second implementation. The only non-mechanical
startup adjustment is conditional wiring for adapters that require unavailable test infrastructure.
