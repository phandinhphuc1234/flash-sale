---
name: spring-clean-feature-architecture
description: Design, review, or refactor the internal package structure of a Java/Spring Boot microservice or business feature using package-by-feature plus pragmatic Clean/Hexagonal Architecture. Use when deciding where controllers, request/response DTOs, use cases, ports, JPA entities/repositories, MapStruct mappers, exceptions/error codes, security, logging, configuration, and tests belong. Do not use for system-wide microservice decomposition or non-Java stacks.
---

# Spring Clean Feature Architecture

Design or review the inside of one Spring Boot microservice or one business feature. Organize by business capability first, then enforce inward dependencies with pragmatic Clean Architecture / Ports and Adapters.

## Read references selectively

- Read `references/decision-guide.md` first for scope and complexity decisions.
- Read `references/package-templates.md` when producing a package tree.
- Read `references/feature-internal-organization.md` when deciding whether a feature needs nested
  `model`, `event`, `exception`, `policy`, `service`, `request`, `response`, `mapper`, `error`, or
  technology-specific adapter packages.
- Read `references/placement-matrix.md` when placing concrete classes.
- Read `references/code-patterns.md` only when code skeletons or implementation are requested.
- Read `references/review-checklist.md` when auditing or refactoring an existing service.
- Read `references/source-notes.md` only when deeper rationale or the original detailed notes are needed.

## Workflow

1. Inspect the repository before proposing structure.
   - Read `AGENTS.md`, build files, the service source tree, existing conventions, tests, migrations, and API/event contracts.
   - Identify the service root, base package, business features, inbound adapters, outbound dependencies, and database ownership.
   - Do not invent infrastructure or abstractions that the requested scope does not need.

2. Decide the package boundary.
   - If the microservice contains multiple meaningful business capabilities, package by feature at the top level; place `domain`, `application`, and `adapter` inside each feature.
   - Keep use cases that operate on the same Aggregate lifecycle in one feature. For example,
     create, get, cancel, and archive Order are use cases inside `order`, not four sibling features.
   - Prefer an explicit feature package even when the first feature matches the service name when
     sibling capabilities are expected, the repository consistently navigates by feature, or the
     feature needs a substantial internal tree. In that case, `order.order` is deliberate rather
     than accidental duplication.
   - If the service truly has one small, stable capability, place `domain`, `application`, and
     `adapter` directly under the service root to avoid a redundant wrapper.

3. Classify business complexity before selecting ceremony.
   - `SIMPLE_CRUD`: reference data or administration with little invariant logic. Do not create fake aggregates, ports, policies, or duplicate models without a real boundary need.
   - `MODERATE`: several rules, external calls, or multiple input/output adapters. Use application use cases and outbound ports; separate persistence types when useful.
   - `CORE_DOMAIN`: important invariants, lifecycle transitions, concurrency, idempotency, or event-driven behavior. Use full domain/application/adapter separation, explicit ports, separate persistence representation, domain events, and architecture tests.

4. Produce or implement a structure that follows these dependency directions.

   ```text
   adapter.in  ─────► application ─────► domain
   adapter.out ─────► application.port.out
   adapter.out ─────► domain
   ```

   Never allow:

   ```text
   domain      ─X─► Spring MVC / Spring Security / JPA / Hibernate / Kafka / MapStruct
   application ─X─► controller DTOs / JpaRepository / JPA entities / HTTP clients
   feature A   ─X─► feature B adapter internals or persistence package
   ```

5. Place classes by boundary, not by vague technical buckets.
   - HTTP requests/responses and web mappers belong to the inbound web adapter.
   - Commands, queries, results, use-case interfaces, and application services belong to application.
   - Aggregates, value objects, domain policies, domain services, domain events, and invariant exceptions belong to domain.
   - JPA entities, Spring Data repositories, persistence mappers, and persistence adapters belong to the outbound persistence adapter.
   - Kafka consumers are inbound messaging adapters; publishers are outbound messaging adapters.
   - HTTP clients are outbound client adapters behind application-defined ports.
   - Security, observability, serialization, and framework configuration may be service-wide technical packages, but business concepts must remain feature-owned.
   - Subdivide a boundary by responsibility only when real code justifies it. A rich domain may use
     `model`, `event`, `exception`, `policy`, and `service`; a small feature may keep two or three
     classes directly under `domain`.
   - Follow the service's established technical package names such as `websupport`, `security`,
     `observability`, and `configuration`. Do not introduce a parallel `shared/config/bootstrap`
     convention when those packages already own the responsibilities.
   - Reuse a repository-level technical library such as `common-web` when it already owns stable
     envelopes; do not duplicate those types in every service.

6. Apply pragmatic rules.
   - Keep the domain pure Java.
   - A controller handles transport concerns and invokes a use case; it does not own business logic.
   - Prefer one application service per cohesive use case instead of a giant generic service class.
   - Create a domain service only for pure domain behavior spanning multiple domain objects. If the
     behavior needs persistence, a broker, a cache, or a remote service, orchestrate it in application
     through an output port.
   - Put transaction boundaries at the application use-case level.
   - Keep `JpaRepository` and JPA annotations in persistence adapters.
   - Do not pass request/response DTOs, `Jwt`, `ResponseEntity`, JPA entities, or framework exceptions into domain logic.
   - Do not expose Spring Data `Page` or `Pageable` through application ports. Map them inside the
     persistence adapter to application-owned page queries/results, then build the shared HTTP page
     envelope in the web adapter.
   - Use separate MapStruct mappers per boundary; never create one global mapper for HTTP, persistence, Kafka, and clients.
   - Domain exceptions describe business invariant failures and do not know HTTP status codes.
   - Translate exceptions to API error codes and HTTP responses at the web boundary.
   - Log at boundaries and state transitions; do not log and rethrow the same exception in every layer.
   - Never log credentials, access tokens, refresh tokens, passwords, payment secrets, or entire sensitive requests.
   - Share only technical cross-cutting code. Do not share domain entities, repositories, business services, statuses, or feature exceptions across microservices.
   - When a committed state change must publish an event reliably, use one atomic persistence
     capability such as `PersistOrderWithOutboxPort`. Do not model required delivery as an
     uncoordinated `SaveOrderPort` followed by `PublishOrderEventPort`.

7. Handle cross-feature communication explicitly.
   - Expose a small feature API or application port, or publish an application/domain event.
   - Do not query another feature's repository or import its adapter classes.
   - For cross-microservice communication, use an HTTP/event contract or a local snapshot; never query another service's database.

8. Add enforcement proportional to the project.
   - Domain tests: pure unit tests without Spring.
   - Application tests: use mocked/fake outbound ports.
   - Web tests: `@WebMvcTest` or the relevant web slice.
   - Persistence tests: `@DataJpaTest`, preferably with Testcontainers for database-specific behavior.
   - Critical flows: focused integration tests.
   - Add ArchUnit rules when maintaining architectural boundaries matters; do not rely only on documentation.

## Required output for design or review requests

Return the following in order:

1. **Scope and complexity classification** — service vs feature and `SIMPLE_CRUD`, `MODERATE`, or `CORE_DOMAIN`.
2. **Recommended package tree** — only folders and files justified by the requested scope.
3. **Dependency rules** — allowed and forbidden directions.
4. **Placement decisions** — explain where each requested class belongs and why.
5. **One runtime flow** — for example HTTP request → mapper → use case → port → persistence adapter → database.
6. **Testing and architecture enforcement**.
7. **Over-engineering check** — explicitly state which layers or abstractions were intentionally omitted.
8. For an existing codebase, include a **migration sequence** that preserves behavior and avoids a big-bang rewrite.

## Implementation behavior

When asked to implement or refactor:

- Preserve existing public contracts unless the user requests a breaking change.
- Move code in small coherent steps and keep the build green.
- Do not create empty placeholder packages solely to match a diagram.
- Do not copy the full reference tree into every feature. Create a nested package together with its
  first real responsibility.
- Do not rename everything when a smaller boundary correction is sufficient.
- Add or update tests and architecture rules for changed boundaries.
- Report changed files, remaining violations, tests run, and any deliberate compromises.
