# Research: Liquibase Migration Setup

## Decision: Add Liquibase only to database-owning services

**Rationale**: The Constitution requires service-owned schemas and migrations. `api-gateway` is stateless and has no schema, so adding a migration runtime there would create unnecessary coupling and dependency surface.

**Alternatives considered**:

- Add Liquibase to all nine modules: rejected because gateway owns no database.
- Keep Liquibase docs-only: rejected because the user asked to set up migration foundations in service projects.

## Decision: Add empty master changelogs, not first changesets

**Rationale**: The user explicitly asked not to create a first migration. An empty master changelog makes the location visible and prevents later features from inventing local conventions.

**Alternatives considered**:

- Add placeholder `changeSet`: rejected because it would be a migration.
- Add sample SQL files: rejected because samples inside service resources can be confused with production migrations.

## Decision: Do not add JPA, PostgreSQL driver, or datasource yet

**Rationale**: There is no approved schema feature. Adding datasource dependencies or config would imply runtime database behavior that does not exist.

**Alternatives considered**:

- Add full JPA/PostgreSQL setup now: rejected as scope expansion.

## Official references

- Spring Boot 3.5 documents that adding `org.liquibase:liquibase-core` enables Liquibase migrations on startup when the datasource is available, and that the default master changelog is `db/changelog/db.changelog-master.yaml`.
- Spring Boot also recommends using one schema initialization mechanism, not mixing Liquibase with `schema.sql`, `data.sql`, or Hibernate schema generation.
- Liquibase documents changelogs as source-controlled ledgers of database changes and changesets as individual units of change.
