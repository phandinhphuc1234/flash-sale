# Database Documentation

Each stateful service owns its PostgreSQL schema and Liquibase changelog under
`services/<service>/src/main/resources/db/changelog/`. Documents here explain selected schemas; they
do not own migration execution.

- [`authentication-service-schema.md`](authentication-service-schema.md)
- [`product-service-schema.md`](product-service-schema.md)
- [`inventory-service-schema.md`](inventory-service-schema.md)

For migration execution and safety, read
[`../technology/liquibase-migration-rules.md`](../technology/liquibase-migration-rules.md) and the
[local Docker guide](../../infra/docker/README.md).

Never grant one service access to another service's tables. Cross-service reads use an approved HTTP
contract or an event-owned projection.
