# Feature 019 Validation Evidence

## G1 — Setup and Module Baseline

**Date**: 2026-08-10  
**Scope**: T001–T005; Flash Sale module dependency/configuration/bootstrap baseline  
**Command**: `./mvnw -pl services/flashsale-service -am verify`  
**Result**: PASS — exit status 0  

Summary:

- `common-web` and `kafka-avro-contracts` reactor prerequisites passed.
- Flash Sale Maven dependency resolution and MapStruct compiler configuration passed.
- `FlashSaleConfigurationPropertiesTests`: 2 tests passed, including invalid idempotency-key bound.
- `FlashsaleServiceApplicationTests`: 1 context test passed.
- No production business behavior, schema, Kafka consumer, Redis script, or public endpoint was
  implemented in G1.
- Maven emitted Mockito dynamic-agent and SLF4J provider warnings; no test failure occurred.

**Rerun**: After aligning default URLs with the shared Docker Compose service names, the same module
verification was rerun and passed with exit status 0.

Future task groups append their command, scope, exit status, and failure details below this section.

## G2 — Foundational Boundaries

**Date**: 2026-08-10
**Scope**: T006–T014; Liquibase schema, independent JWT trust, HTTP error envelope, W3C/MDC
context, and Clean/Hexagonal architecture checks
**Command**: `./mvnw -pl services/flashsale-service -am verify`
**Result**: PASS — exit status 0

Summary:

- Liquibase formatted SQL defines the four service-owned durable tables, PostgreSQL checks/indexes,
  unique business identities, and an explicit development rollback order; a real PostgreSQL 16
  Testcontainers run applied all eight changesets successfully.
- JWT trust validates issuer, `flash-sale-api` audience, `typ=at+jwt`, expiry/signature through the
  Nimbus decoder, and UUID `sub`; security failures use sanitized shared 401/403 envelopes.
- Flash Sale errors use the shared `ApiErrorResponse`/`FieldViolation` shape with `X-Trace-Id` and
  `Cache-Control: no-store`; trace IDs are not copied into JSON bodies.
- W3C `traceparent` extraction and MDC request context are bounded, and observation names are
  stable low-cardinality constants.
- Architecture tests reject provider imports in domain/application and keep Redis/Kafka at adapter
  or configuration boundaries.
- Flash Sale tests: 14 passed in the module verification, including 3 migration integration tests
  against PostgreSQL 16; `common-web` (9) and Kafka contract (3) reactor tests also passed.
- No user-story behavior, Redis Lua script, Kafka consumer, outbox worker, or public reservation
  endpoint was implemented in G2.
- Maven emitted only existing Mockito dynamic-agent and SLF4J provider warnings; no test failed.
