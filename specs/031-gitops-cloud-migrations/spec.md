# Feature Specification: Cloud Database Migration Gates

**Feature Branch**: `031-gitops-cloud-migrations`

**Created**: 2026-08-21

**Status**: Approved

**Input**: User request to continue the cloud GitOps rollout after Phase 15 Secret provisioning.

## Problem and Scope

Phase 15 intentionally disabled Liquibase in the eight cloud application ConfigMaps. Starting all
Deployments before their service-owned schemas exist would make readiness failures ambiguous and
could allow application code to race a schema change. Phase 16 provides an explicit, repeatable
database migration gate using each service's own container image and database credentials.

### In scope

- Run Liquibase for Authentication, Product, Campaign, Flash Sale, Inventory, Order, and Payment.
- Use the existing PostgreSQL StatefulSet and service-owned database names.
- Keep migration Jobs separate from the canonical application overlay so Deployments do not start
  before the operator chooses to roll them out.
- Disable Kafka listeners, schedulers, outbox publishers, and business consumers in migration Jobs.
- Add validation-only and explicit `-Apply` PowerShell orchestration.
- Record completion and failure evidence without reading Secret data.

### Out of scope

- Java source, database changelog, HTTP/Kafka contract, or service boundary changes.
- Cart and Notification, which are not in the approved cloud topology.
- Stripe enablement, public ingress, autoscaling, backups, or production migration tooling.
- Running the migration against EKS automatically from CI or from Argo CD.

## User Stories and Acceptance Scenarios

### US1 — Validate migration prerequisites (P1)

As an operator, I want a dry-run that checks the cloud migration overlay, platform, and Secret names
without reading Secret values.

**Acceptance scenarios**

1. With the Phase 15 Secrets and PostgreSQL available, validation succeeds and prints resource names
   only.
2. If a required Secret or platform StatefulSet is missing, validation exits non-zero and names the
   missing resource.

### US2 — Migrate service-owned schemas (P1)

As an operator, I want one Kubernetes Job per service so each schema is migrated by the service that
owns it.

**Acceptance scenarios**

1. `-Apply` creates eight Jobs from service images and waits for each Job to complete.
2. Jobs use only their service Secret and datasource URL, never another service's database.
3. Jobs do not start Kafka consumers, schedulers, or outbox publishers.

### US3 — Preserve safe reruns and evidence (P2)

As an operator, I want failed Jobs to remain inspectable and reruns to require an explicit choice.

**Acceptance scenarios**

1. A failed Job stops orchestration and remains available for `kubectl describe` and logs.
2. A second run refuses to replace existing Jobs unless `-ForceRerun` is supplied.
3. No command prints Secret values or PEM contents.

## Requirements

- **FR-001**: The migration overlay MUST contain exactly seven service-owned Jobs; API Gateway is
  stateless and has no database migration.
- **FR-002**: Each Job MUST use its service ConfigMap and service Secret from Phase 15.
- **FR-003**: Each Job MUST connect to the service's own PostgreSQL database.
- **FR-004**: Each Job MUST enable Liquibase only for the migration process, including Product's
  hard-coded disabled default via an explicit Spring command-line override.
- **FR-005**: Jobs MUST disable listeners, schedulers, consumers, and outbox publishers.
- **FR-006**: The script MUST be validation-only by default and require explicit `-Apply`.
- **FR-007**: The script MUST stop on a failed Job and preserve its status for inspection.
- **FR-008**: Rerunning existing Jobs MUST require explicit `-ForceRerun`.
- **FR-009**: No Secret value, PEM body, Java source, contract, or migration SQL may be added.

## Non-functional requirements

- **NFR-SEC-001**: Secret data is referenced by name only; no `kubectl get secret -o yaml` is used.
- **NFR-OPS-001**: A new operator can validate prerequisites and understand the next command from the
  quickstart.
- **NFR-VAL-001**: Kustomize render and client-side dry-run pass before any live Job is created.

## Manual decisions

None. Phase 16 uses the already approved Phase 15 Secret names and database topology. Stripe and
public HTTPS remain deferred.

## Success criteria

- **SC-001**: Seven migration Jobs render with service-specific ConfigMap/Secret references; API
  Gateway is intentionally excluded because it owns no database.
- **SC-002**: Validation-only mode makes no cluster mutation and succeeds against the prepared EKS
  namespace.
- **SC-003**: Apply mode waits for completed Jobs and stops on the first failure.
- **SC-004**: No migration resource references the legacy global `flash-sale-secrets`.
