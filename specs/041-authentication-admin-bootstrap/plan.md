# Implementation Plan: One-Time Authentication Admin Bootstrap

**Branch**: `codex/auth-admin-bootstrap` | **Date**: 2026-08-22 | **Spec**: [spec.md](spec.md)

## Summary

Add a disabled-by-default Authentication application use case for creating one active
`ROLE_ADMIN`, expose it only through an `ApplicationRunner`, and run it from a disposable Kubernetes
Job. The root PowerShell launcher creates a temporary Secret from operator input, uses the currently
deployed Authentication image, waits for completion, and cleans the Secret and Job. No SQL, schema,
public API, or new dependency is added.

## Technical Context

**Language/Version**: Java 21, PowerShell 7, Kubernetes YAML

**Primary Dependencies**: Existing Spring Boot 3.x, Spring Data JPA, configured Argon2 password
adapter, Kubernetes Job; no new production dependency

**Storage**: Existing Authentication-owned PostgreSQL `auth_db.users`

**Testing**: Domain/application unit tests, Authentication module Maven verify, PowerShell script
validation tests, Kubernetes client-side dry run, and live one-time Job plus Phase 22 smoke

**Target Platform**: EKS cloud overlay and the existing Authentication container image

**Project Type**: Independent Spring Boot microservice plus root GitOps operational script

**Performance Goals**: One bounded bootstrap operation; no throughput target

**Constraints**: Disabled by default; no credential logging; no direct SQL; no normal Deployment
mutation; idempotent retry and fail-closed collisions

**Scale/Scope**: One operator-created administrator for the development cloud environment

## Constitution Check

- **Specification traceability**: PASS — requirements and operational contract are in Feature 041.
- **Service ownership**: PASS — Account creation, hashing, persistence, and role mapping remain in
  Authentication; root tooling only triggers the use case.
- **Communication**: PASS — no public HTTP or Kafka contract change.
- **Data and messaging**: PASS — existing PostgreSQL remains durable truth; Redis/Kafka untouched.
- **Root infrastructure ownership**: PASS — the launcher is under `infra/scripts/gitops/`; no shared
  platform asset is added inside the service module.
- **Observability**: PASS — existing Actuator configuration is unchanged; runner output is sanitized.
- **Contracts and dependencies**: PASS — operational contract added; no new dependency.
- **Validation**: PASS — unit, module, script, Kubernetes dry-run, and live smoke are specified.

## Design

### Application and domain

`Account.bootstrapAdmin(...)` is a pure domain factory that preserves the distinction between public
registration (`ROLE_USER`) and operator bootstrap (`ROLE_ADMIN`).

`BootstrapAdminAccountService` owns validation, normalized identity collision rules, password policy,
idempotent same-admin behavior, and fail-closed outcomes. It depends only on application ports and
the existing domain model.

### Adapters and wiring

```text
ApplicationRunner (adapter/in/bootstrap)
  -> BootstrapAdminAccountUseCase
  -> BootstrapAdminAccountService
  -> AdminBootstrapAccountPort
  -> JpaAccountPersistenceAdapter -> auth_db.users
```

The runner reads `AdminBootstrapProperties` only when `AUTH_ADMIN_BOOTSTRAP_ENABLED=true`. The
existing `EncodePasswordPort` is reused; its Argon2 adapter remains the provider boundary.

### Kubernetes launcher

`phase22-admin-bootstrap.ps1` discovers the current Authentication Deployment image, validates
required ConfigMaps/Secrets, creates a temporary three-key Secret, and applies a Job with
`SPRING_MAIN_WEB_APPLICATION_TYPE=none`. It waits for completion, prints status only, and deletes
the temporary Secret and Job by default. It never edits the Argo-managed Deployment or desired-state
Kustomization.

### Compatibility and failure behavior

- Existing public registration and login contracts remain unchanged.
- Existing `ROLE_ADMIN` login tokens retain all current authorities.
- A unique-constraint race is surfaced as a bootstrap conflict; no retry can elevate a user.
- A failed Job is safe to rerun after operator review; the password is never reused implicitly.

## Project Structure

```text
services/authentication-service/src/main/java/com/philia/flashsale/authentication/
├── account/
│   ├── domain/Account.java
│   ├── application/bootstrap/
│   ├── adapter/in/bootstrap/
│   └── adapter/out/persistence/JpaAccountPersistenceAdapter.java
└── configuration/
    └── AdminBootstrapProperties.java
services/authentication-service/src/test/java/.../account/
├── application/bootstrap/BootstrapAdminAccountServiceTests.java
└── domain/AccountTests.java
infra/scripts/gitops/phase22-admin-bootstrap.ps1
specs/041-authentication-admin-bootstrap/
└── spec.md, plan.md, research.md, data-model.md, quickstart.md, contracts/, tasks.md
```

## Test Strategy

- Pure domain tests cover admin factory role/status and identity normalization.
- Application tests cover creation, idempotent existing admin, user collision, mismatched identities,
  password validation, and no password replacement.
- Existing Authentication integration tests continue to prove JWT authority mapping.
- Script tests cover validation-only behavior, secure prompt marker, cleanup commands, and refusal to
  print secret values.
- `./mvnw.cmd -pl services/authentication-service -am verify` is required.
- `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` remains required for the affected
  cloud overlay; the launcher itself performs a client dry run for its generated Job.
- Live evidence is the successful Job plus Phase 22 authenticated smoke.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected |
|---|---|---|
| Disposable Job + launcher | Keeps bootstrap explicit and prevents normal Deployment reruns | Enabling the long-running Deployment would make every restart a credential operation |
| Dedicated bootstrap application ports | Separates privileged provisioning semantics from public registration | Reusing public registration would always create `ROLE_USER` and blur the security boundary |

## Approval and History

- 2026-08-22 — Plan approved together with Feature 041 by project owner in chat.
