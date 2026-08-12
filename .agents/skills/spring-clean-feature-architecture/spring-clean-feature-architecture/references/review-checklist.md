# Review checklist

## Feature boundary

- [ ] Top-level packages represent business capabilities, not only technical layers.
- [ ] A feature is cohesive and not merely one endpoint.
- [ ] Use cases for the same Aggregate lifecycle were not split into artificial sibling features.
- [ ] A single-feature service avoids redundant package naming unless future sibling features justify it.
- [ ] No feature imports another feature's persistence or adapter internals.
- [ ] Nested packages such as `policy`, `service`, `mapper`, and `error` contain real responsibilities rather than placeholders.

## Domain

- [ ] Domain code is pure Java.
- [ ] Aggregates/value objects enforce important invariants.
- [ ] Domain exceptions do not reference HTTP or framework types.
- [ ] No JPA, Spring MVC, Spring Security, Kafka, or MapStruct imports in domain.
- [ ] CRUD-only features are not forced into fake DDD abstractions.

## Application

- [ ] Use cases are cohesive; there is no giant service with unrelated operations.
- [ ] Application inputs are commands/queries, not HTTP DTOs.
- [ ] Application outputs are results/domain values, not `ResponseEntity` or JPA entities.
- [ ] Application ports do not expose Spring Data `Page`, `Pageable`, or `Sort`.
- [ ] External capabilities are represented by outbound ports when a useful boundary exists.
- [ ] Transaction boundaries align with use cases.
- [ ] Remote calls are not wrapped in a long database transaction without a deliberate reason.
- [ ] Required durable state plus event publication uses an approved atomic outbox capability.

## Inbound adapters

- [ ] Controllers contain only transport/authentication/mapping concerns.
- [ ] Bean Validation handles request shape; business validation remains in domain/application.
- [ ] Kafka consumers map event contracts to application inputs.
- [ ] API error codes are stable and feature-owned.

## Outbound adapters

- [ ] `JpaRepository`, JPA entities, queries, and persistence mappers remain inside persistence adapters.
- [ ] JPA entities do not leak into controllers or application APIs.
- [ ] Persistence/client exceptions are translated before crossing inward.
- [ ] HTTP clients and Kafka publishers implement application-defined capabilities.
- [ ] A use case does not assume that an unrelated save followed by direct publish is atomic.

## Mapping

- [ ] Web, persistence, event, and client mapping are separate.
- [ ] No global mapper knows every representation.
- [ ] Mapping does not silently bypass domain constructors/invariants.

## Error handling and logging

- [ ] `GlobalExceptionHandler` translates known failures at the HTTP boundary.
- [ ] Feature API error codes remain feature-owned even when one service-wide handler formats them.
- [ ] The same exception is not logged at repository, application, controller, and handler layers.
- [ ] Structured logs carry service, event, trace/correlation IDs, identifiers, and outcome where useful.
- [ ] Tokens, passwords, payment data, and sensitive request bodies are never logged.

## Shared code

- [ ] Shared packages contain only technical cross-cutting code.
- [ ] Service-local shared code does not duplicate an existing repository-level technical contract.
- [ ] `shared/config/bootstrap` does not duplicate established `websupport/security/observability/configuration` ownership.
- [ ] No domain entity, status, repository, business service, or feature mapper is shared across microservices.
- [ ] Cross-service integration uses explicit HTTP/event contracts or local snapshots.

## Tests

- [ ] Domain tests run without Spring.
- [ ] Application tests replace outbound ports with fakes/mocks.
- [ ] Web and persistence use focused test slices.
- [ ] Database-specific behavior uses a realistic database/Testcontainers where needed.
- [ ] Critical workflows have integration tests.
- [ ] ArchUnit or Spring Modulith verifies important boundaries.

## Refactoring sequence

1. Freeze public behavior with tests.
2. Identify feature boundaries and current dependency violations.
3. Move HTTP DTO/controller code to inbound adapters.
4. Extract commands/results and cohesive use cases.
5. Introduce outbound ports at real external boundaries.
6. Move JPA and client implementations behind adapters.
7. Separate domain and persistence models only where justified.
8. Translate exceptions at boundaries.
9. Add ArchUnit rules after packages stabilize.
10. Remove obsolete generic `service`, `repository`, `dto`, `mapper`, and `utils` buckets.
