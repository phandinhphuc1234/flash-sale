# Feature Specification: Liquibase Migration Setup

**Feature Branch**: `004-liquibase-migration-setup`

**Created**: 2026-07-14

**Status**: Approved

**Input**: User description: "Create migration rules with Liquibase and set up migration in each service project. Do not create the first migration for any service yet; only set up the migration foundation."

## User Scenarios & Testing

### User Story 1 - Follow one migration rulebook (Priority: P1)

As a developer, I can read one repository rulebook for Liquibase migrations, so future schema work follows the same service-owned process.

**Independent Test**: Open the migration rules doc and confirm it explains service ownership, changelog structure, changeset rules, rollback expectations, expand-and-contract, and validation requirements.

### User Story 2 - Start future service migrations from a ready foundation (Priority: P2)

As a service owner, I can add the first real migration to my service without creating the Liquibase foundation from scratch.

**Independent Test**: Inspect each database-owning service and confirm it has the Liquibase dependency, a configured master changelog path, and an empty changelog folder with no business migration changeset.

## Requirements

- **FR-001**: The repository MUST document Liquibase migration rules for service-owned schemas.
- **FR-002**: Database migrations MUST remain under the owning service and MUST NOT move to root `infra/`.
- **FR-003**: The eight database-owning service modules MUST declare `org.liquibase:liquibase-core` without an explicit version.
- **FR-004**: `api-gateway` MUST NOT receive Liquibase setup because it is currently stateless and owns no database.
- **FR-005**: Each database-owning service MUST contain `src/main/resources/db/changelog/db.changelog-master.yaml`.
- **FR-006**: Each database-owning service MUST contain a `src/main/resources/db/changelog/changes/` folder for future changesets.
- **FR-007**: No service MUST receive a first business changeset, table, index, constraint, seed data, or rollback script in this feature.
- **FR-008**: Each database-owning service MUST configure the Liquibase changelog path in `application.yml`.
- **FR-009**: Documentation MUST state that future JPA services use Hibernate `ddl-auto=validate`, not `update`, when Liquibase owns schema changes.
- **FR-010**: Documentation MUST state that future schema changes require PostgreSQL/Testcontainers validation where applicable.

## Success Criteria

- **SC-001**: Eight service POMs contain `liquibase-core`; `api-gateway` does not.
- **SC-002**: Eight services contain an empty master changelog and future `changes/` folder.
- **SC-003**: Inspection finds zero Liquibase `changeSet` entries.
- **SC-004**: Existing Spring Boot context tests still pass.

## Assumptions

- The future database-owning services are `authentication-service`, `product-service`, `campaign-service`, `flashsale-service`, `order-service`, `payment-service`, `notification-service`, and `chatting-service`.
- `api-gateway` remains stateless and owns no schema.
- PostgreSQL, JPA, actual datasource properties, and first business migrations will be introduced by later approved features.

## Constitutional Constraints

- **Service ownership**: Migrations are service-owned. No shared migration module or root migration directory is introduced.
- **External ingress**: No gateway route or ingress behavior changes.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: No table, schema, durable state, or Redis script is introduced.
- **Messaging reliability**: No Kafka, outbox table, producer, or consumer is introduced.
- **Root infrastructure ownership**: No root `infra/` runtime asset changes.
- **Observability**: Existing health and Prometheus setup remains unchanged.
- **Verification**: Static migration inspection and full Maven verification apply.
- **Architecture decisions**: No ADR is required because this follows the existing Constitution rule that migrations are service-owned.
