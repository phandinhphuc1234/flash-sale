# Contract: Liquibase Migration Setup

This is a repository convention contract, not an HTTP or Kafka contract.

## Required Service Setup

Every database-owning service must have:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
src/main/resources/db/changelog/changes/
```

The master changelog must be valid YAML and must contain no business changesets until an approved schema feature adds them.

## Excluded Service

`api-gateway` must not include Liquibase setup while it remains stateless and owns no database.

## Future Changeset Rules

Future migrations must:

- add a new changeset instead of editing one already applied in shared environments
- use unique, meaningful ids
- include rollback guidance for risky changes
- document lock/backfill impact for large indexes, constraints, and data updates
- be validated with PostgreSQL/Testcontainers where applicable
- stay under the service that owns the schema

Future migrations must not:

- live under root `infra/`
- update another service database
- rely on Hibernate `ddl-auto=update`
- mix Liquibase with `schema.sql` or `data.sql`
