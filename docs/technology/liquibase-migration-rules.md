# Liquibase Migration Rules

Liquibase is the migration standard for service-owned PostgreSQL schemas in this repository. This file defines the rules before any service adds its first real migration.

## Ownership

Each service owns its own changelog:

```text
services/<service>/src/main/resources/db/changelog/
```

Do not create a root migration folder under `infra/`. Root infrastructure may provision isolated databases, but it must not own business schema changes.

`api-gateway` remains excluded while it is stateless and owns no database.

## Runtime Flow

When a service has an approved schema, a one-off migration process runs Liquibase before application
replicas are rolled out:

```text
migration process starts
  -> connect to service-owned database
  -> acquire DATABASECHANGELOGLOCK
  -> read db.changelog-master.yaml
  -> run unapplied changesets
  -> record results in DATABASECHANGELOG
  -> release lock
  -> migration process exits successfully
  -> application replicas start with migrations disabled
```

Liquibase's changelog is a source-controlled ledger. A changeset is one unit of change inside that ledger.

## Required Structure

Every database-owning service uses:

```text
src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── changes/
```

The master changelog is the index. Future changesets go under `changes/` and are included from the master file in execution order.

## Current Baseline

Service shells without an approved business schema keep an empty master changelog:

```yaml
databaseChangeLog: []
```

That is intentional. No service gets a first table, seed data, Outbox table, or rollback script until
an approved schema feature needs it. `product-service` now has the first real service-owned changeset:

```text
changes/001-create-product-catalog-schema.sql
```

Normal Product replicas still keep Liquibase disabled; local Compose and future Kubernetes run the
same changelog through a one-off migration process.

## Future Changeset Rules

1. Never edit a changeset that has already run in a shared environment.
2. Add a new changeset for every schema change.
3. Use meaningful ids, for example `001-create-orders-table`.
4. Keep author names stable and human-readable.
5. Include rollback guidance for risky changes.
6. Prefer forward fixes in production when rollback could lose data.
7. Do not drop columns or tables in the same release that stops using them.
8. Use expand-and-contract for breaking schema changes.
9. Analyze lock time for large indexes, constraints, and backfills.
10. Validate migrations against real PostgreSQL, preferably with Testcontainers.

## Hibernate Rule

When a future service adds JPA, Hibernate must validate the schema instead of changing it:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Do not use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

Liquibase owns schema changes. Hibernate checks that the mapping matches the schema.

## Script Initialization Rule

Do not mix Liquibase with production `schema.sql` or `data.sql`. Spring Boot recommends using one schema initialization mechanism, and this repository chooses Liquibase for service-owned schemas.

## YAML vs Formatted SQL

Use YAML for portable structural changes when it remains clear and reviewable:

```text
001-create-orders-table.yaml
002-add-idempotency-key.yaml
```

Use Liquibase formatted SQL when PostgreSQL-specific constraints, JSONB, partial indexes, or exact
DDL are clearer or safer:

```sql
--liquibase formatted sql
--changeset philia:002-enforce-one-primary-category dbms:postgresql runInTransaction:true
CREATE UNIQUE INDEX uq_product_categories_one_primary
    ON product_categories (product_id)
    WHERE is_primary = TRUE;
--rollback DROP INDEX uq_product_categories_one_primary;
```

Formatted SQL rollback must be written explicitly.

`CREATE INDEX CONCURRENTLY` cannot run inside the normal transactional changeset pattern. A future
hot-table concurrent-index migration requires an explicit `runInTransaction:false` plan, operational
risk review, and separate failure/retry instructions; do not copy it into an ordinary changeset.

## Production Review Checklist

Before approving a future migration, check:

- Does the migration belong to this service's database?
- Is the changeset new rather than editing history?
- Is the id unique and meaningful?
- Is rollback or forward-fix behavior documented?
- Could the change lock a hot table?
- Does it require backfill batching?
- Is the application code compatible before and after deploy?
- Has it been tested against PostgreSQL?
- Is `ddl-auto=validate` used if JPA exists?
- Are `schema.sql` and `data.sql` absent for production schema changes?

## References

- [Spring Boot database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
- [Liquibase Spring Boot integration](https://contribute.liquibase.com/extensions-integrations/directory/integration-docs/springboot/)
- [Liquibase changelogs and changesets](https://docs.liquibase.com/concepts/changelogs/home.html)
