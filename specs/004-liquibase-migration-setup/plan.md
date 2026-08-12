# Implementation Plan: Liquibase Migration Setup

**Branch**: `004-liquibase-migration-setup` | **Date**: 2026-07-14 | **Spec**: [spec.md](spec.md)

## Summary

Add a Liquibase migration foundation for the eight database-owning service modules while keeping `api-gateway` stateless. The change adds `liquibase-core`, an empty master changelog, a future changeset folder, declarative `spring.liquibase.change-log` configuration, and a repository rulebook. It intentionally does not add JPA, PostgreSQL drivers, datasource settings, business changesets, tables, seed data, or migration scripts.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.16

**Primary Dependencies**: Existing Spring Boot service dependencies plus `org.liquibase:liquibase-core` in database-owning services

**Storage**: No live storage introduced; PostgreSQL remains planned

**Testing**: Full Maven reactor verification and static inspection for zero `changeSet` entries

**Target Platform**: Existing Maven monorepo with independently runnable Spring Boot services

**Project Type**: Java microservice monorepo

**Performance Goals**: No runtime migration workload is introduced because no datasource or changesets are added

**Constraints**: No first migration; no JPA/PostgreSQL dependency; no datasource; no root-owned migration directory; no gateway Liquibase dependency

## Constitution Check

*GATE result before design: PASS. Re-check after design: PASS.*

- **Specification traceability**: This plan maps FR-001 through FR-010 to docs, POMs, resource files, configuration, and validation.
- **Service ownership**: Changelogs are placed under each owning service. No service writes another service schema.
- **Communication**: No gateway route, HTTP contract, Kafka contract, or gRPC contract is added.
- **Data and messaging**: No schema, table, Redis state, Kafka consumer, outbox worker, or durable data is introduced.
- **Root infrastructure ownership**: Shared infrastructure remains untouched. Business-schema migration files stay under services, not `infra/`.
- **Observability**: Existing Actuator and Prometheus behavior remains unchanged.
- **Contracts and dependencies**: `liquibase-core` is the only new production dependency, justified as the migration engine for future service-owned PostgreSQL schemas. It is not added to `api-gateway`.
- **Validation**: Run `.\mvnw.cmd clean verify` because this is cross-service production dependency setup. Inspect POMs and changelog files for scope.

## Project Structure

```text
docs/
└── technology/
    └── liquibase-migration-rules.md

services/<database-owning-service>/
├── pom.xml
└── src/main/resources/
    ├── application.yml
    └── db/changelog/
        ├── db.changelog-master.yaml
        └── changes/
            └── .gitkeep
```

Database-owning services:

- `authentication-service`
- `product-service`
- `campaign-service`
- `flashsale-service`
- `order-service`
- `payment-service`
- `notification-service`
- `chatting-service`

Excluded service:

- `api-gateway`, because it remains stateless and owns no schema.

## Dependency Decisions

| Dependency | Scope | Modules | Justification |
|---|---|---|---|
| `org.liquibase:liquibase-core` | compile/default | eight database-owning services | Enables Spring Boot Liquibase auto-configuration when a future datasource exists; version is managed by Spring Boot parent |

Do not add `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, or datasource properties in this feature. Those belong to future schema-owning features.

## Configuration Design

Each database-owning service receives:

```yaml
spring:
  liquibase:
    enabled: ${LIQUIBASE_ENABLED:true}
    change-log: classpath:/db/changelog/db.changelog-master.yaml
```

The changelog path is explicit even though it matches Spring Boot's default. This makes the convention visible in each service.

## Verification Plan

1. Inspect eight service POMs for `liquibase-core`.
2. Inspect `api-gateway/pom.xml` and confirm Liquibase is absent.
3. Inspect each database-owning service for `db/changelog/db.changelog-master.yaml` and `changes/.gitkeep`.
4. Search for `changeSet` and confirm none exist in service changelogs.
5. Run `.\mvnw.cmd clean verify` from the repository root.

## Complexity Tracking

No constitutional violation or architecture exception is required.
