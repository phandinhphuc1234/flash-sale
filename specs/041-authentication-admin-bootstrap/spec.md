# Feature Specification: One-Time Authentication Admin Bootstrap

**Feature Branch**: `041-authentication-admin-bootstrap`
**Created**: 2026-08-22
**Status**: Approved
**Input**: User request to provision one cloud `ROLE_ADMIN` account so the approved Phase 22 authenticated smoke can run.
**Business Owner**: Project owner / platform operator
**Required Reviewers**: Authentication owner, platform operator

## Problem and Scope

### Problem Statement

The cloud `auth_db` currently contains no users. Phase 22 intentionally requires an existing
administrator and must not elevate a public account or edit `auth_db` from an operator script.
The project therefore needs a bounded, one-time provisioning path owned by
`authentication-service`.

### In Scope

- An opt-in Authentication application use case that creates one administrator account when the
  supplied normalized email and username do not exist.
- A Kubernetes Job launcher under root `infra/scripts/gitops/` that runs the deployed
  Authentication image with credentials supplied through a temporary Kubernetes Secret.
- Idempotent rerun behavior for the same existing administrator identity without changing its
  password.
- Fail-closed collision behavior when either identity belongs to a non-admin account or to a
  different account.
- Documentation for the manual secret boundary, cleanup, and Phase 22 handoff.

### Out of Scope

- A public HTTP admin-creation or role-promotion endpoint.
- Password reset, account deletion, multiple administrators, or an admin UI.
- Direct SQL/`kubectl exec` data mutation, Liquibase seed rows, or hard-coded credentials.
- Changes to the public registration contract; public registration continues to create `ROLE_USER`.

## User Scenarios & Testing

### User Story 1 - Provision a safe cloud administrator (Priority: P1)

As a platform operator, I want to provision one administrator through the owning Authentication
Service so that I can run the authenticated cloud E2E without placing credentials in Git.

**Why this priority**: It unblocks the already approved Phase 22 smoke while preserving service and
credential ownership.

**Independent Test**: With an empty cloud `auth_db`, run the one-time bootstrap Job using a manually
entered password, then log in through Phase 22 and observe all required administrator authorities.

**Acceptance Scenarios**:

1. **Given** bootstrap is disabled, **when** Authentication starts normally, **then** no account is
   created and no bootstrap credential is required.
2. **Given** no account owns the supplied normalized email or username, **when** the opt-in Job runs,
   **then** exactly one active `ROLE_ADMIN` account is persisted with a provider-generated password
   hash and the Job reports success without printing credentials.
3. **Given** the same administrator already exists, **when** the Job is rerun, **then** it reports an
   idempotent success and does not replace the stored password or create a duplicate row.
4. **Given** either identity belongs to a `ROLE_USER` account or a different account, **when** the Job
   runs, **then** it fails closed without changing the existing account.
5. **Given** the Job succeeds, **when** the launcher finishes, **then** the temporary bootstrap Secret
   is deleted and the normal Authentication Deployment remains bootstrap-disabled.
6. **Given** public registration receives privilege-like fields, **when** an account is registered,
   **then** the existing public behavior remains `ROLE_USER`.

### Edge Cases

- Blank, malformed, or too-short bootstrap inputs fail before persistence.
- Duplicate email/username races are converted to a bounded bootstrap conflict rather than a second
  administrator.
- A failed Job keeps sanitized diagnostics available but never prints the password, password hash,
  access token, or Secret data.
- A stale completed Job must not be silently reused; the launcher requires explicit rerun intent.

## Requirements

### Functional Requirements

- **FR-001**: Authentication MUST expose an opt-in, non-HTTP admin bootstrap use case owned by the
  `account` capability.
- **FR-002**: Bootstrap MUST be disabled by default in every normal service Deployment.
- **FR-003**: Bootstrap MUST validate email, username, and the existing 12–128 code-point password
  policy before writing data.
- **FR-004**: A newly created account MUST use the existing `AccountRole.ROLE_ADMIN` mapping and
  `AccountStatus.ACTIVE`.
- **FR-005**: An existing matching `ROLE_ADMIN` identity MUST be treated as idempotent success;
  bootstrap MUST NOT change its password or status.
- **FR-006**: A `ROLE_USER` collision or cross-identity mismatch MUST fail closed and MUST NOT elevate
  or mutate the account.
- **FR-007**: The launcher MUST obtain the password with `Read-Host -AsSecureString`, pass it through
  a temporary Secret, and remove that Secret after the Job completes or fails.
- **FR-008**: The launcher MUST use the currently deployed Authentication image and must not mutate
  the long-running Deployment, ConfigMap, or Git desired state.
- **FR-009**: Bootstrap output MUST be sanitized and MUST NOT contain credentials, hashes, JWTs,
  cookies, or Secret values.
- **FR-010**: The existing public registration endpoint MUST continue to create only `ROLE_USER`.

### Non-Functional Requirements

- **NFR-SEC-001**: No bootstrap credential may be committed to Git, `.env`, a Kustomize manifest, or
  the conversation.
- **NFR-REL-001**: The operation must be safe to retry after a timeout without duplicate identity or
  password replacement.
- **NFR-AUD-001**: Evidence must link the bootstrap Job outcome to the sanitized administrator subject
  and the subsequent Phase 22 login result without exposing secrets.

### Key Entities

- **Authentication Account**: Existing `users` aggregate owned by Authentication; identity is the
  normalized email/username pair and lifecycle is represented by `ROLE_ADMIN` plus `ACTIVE` status.
- **Bootstrap Operation**: A disposable operator-triggered Job execution; it is not durable business
  state and has no new database table.

## Success Criteria

### Measurable Outcomes

- **SC-001**: One clean cloud database run creates exactly one active administrator and Phase 22 login
  verifies `ROLE_ADMIN`, `CATALOG_ADMIN`, `INVENTORY_ADMIN`, and `CAMPAIGN_ADMIN`.
- **SC-002**: A same-identity rerun creates zero additional rows and leaves the original password
  unchanged.
- **SC-003**: A collision run creates zero rows and leaves the colliding `ROLE_USER` unchanged.
- **SC-004**: No required validation output contains a password, hash, token, cookie, or Secret value.

## Assumptions

- The Authentication schema is already migrated and the existing `users` table remains the source of
  truth.
- The operator has permission to create a temporary Secret and Job in the `flash-sale` namespace.
- The deployed Authentication image contains this feature before the launcher is applied.
- The operator removes the temporary Job after retaining sanitized evidence.

## Constitutional Constraints

- **Service ownership**: `authentication-service` owns the Account aggregate, JPA entity, repository,
  and `auth_db`; no other service or root script writes that database.
- **External ingress**: No new public route; the bootstrap is an inbound operational Job adapter.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: Existing PostgreSQL account storage remains durable truth; Redis and
  flash-sale stock are unaffected.
- **Messaging reliability**: No Kafka producer/consumer or outbox change.
- **Root infrastructure ownership**: The disposable Job launcher is under `infra/scripts/gitops/`;
  Authentication runtime and persistence code remain under its service module.
- **Observability**: Existing Actuator configuration remains unchanged; bootstrap logs are sanitized
  and identify only outcome/subject metadata.
- **Verification**: Domain/application unit tests, Authentication module verify, script dry-run, and
  Kubernetes client-side validation are required; no load test applies to this one-time operation.
- **Architecture decisions**: [ADR 0025](../../docs/adr/0025-authentication-admin-bootstrap.md) records
  the one-time operational boundary and rejected direct-SQL alternatives.

## Approval and History

- 2026-08-22 — Drafted from the project owner's request after confirming cloud `auth_db` contains zero users.
- 2026-08-22 — Approved by project owner in chat; fail-closed collisions, temporary Secret, and cleanup accepted.
