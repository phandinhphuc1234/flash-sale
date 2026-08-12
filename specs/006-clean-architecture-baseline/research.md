# Research: Clean Architecture Working Baseline

## Decision: Refine the existing scaffold instead of replacing it

**Rationale**: Feature 002 already established the correct package-level Clean/Hexagonal zones across all nine services. Reorganizing or duplicating them would add churn without improving dependency direction.

**Alternatives considered**:

- Maven module per architecture layer: rejected because it couples build complexity to an empty codebase.
- Feature-first nested packages in every service: deferred until each service has enough real use cases to justify that grouping.
- A second parallel `infrastructure` tree: rejected because `adapter` and root `infra/` already have distinct, documented meanings.

## Decision: Add only `application/query` as a new baseline marker

**Rationale**: Commands and results already have explicit locations, while the approved architecture prompt also distinguishes query input models. Adding the missing peer makes read and write use-case inputs discoverable without implementing either.

**Alternatives considered**:

- Put query inputs in `command`: rejected because it blurs intent.
- Pre-create controller, DTO, mapper, entity, repository, security, scheduler, and provider subtrees: rejected because these are adapter- and feature-specific.
- Add query to `api-gateway`: rejected because the gateway is an edge proxy and does not own business queries.

## Decision: Use create-on-demand nested packages

**Rationale**: A location convention is valuable before code exists, but a physical package should communicate an actual responsibility. Keeping optional nesting in documentation prevents empty-folder sprawl and avoids implying every service needs the same technologies.

**Alternatives considered**:

- Create every possible leaf marker: rejected as speculative and difficult to maintain.
- Allow global `dto`, `mapper`, `entity`, or `utils` packages: rejected because ownership becomes ambiguous and boundary representations become shareable by accident.

## Decision: Co-locate boundary representations and mappers with their adapter

**Rationale**: HTTP DTOs, Kafka payloads, provider payloads, and persistence entities have different owners and compatibility rules. Co-location makes those boundaries visible and keeps the domain independent of transport and storage.

**Alternatives considered**:

- Reuse domain objects as wire and persistence models: rejected because framework and compatibility concerns leak inward.
- One shared mapper package: rejected because it becomes a coupling hub across unrelated boundaries.

## Decision: Validate exact scope rather than add an architecture-test dependency

**Rationale**: There are no layer implementation classes yet, so an architecture library would provide little signal and introduce dependency/configuration work. Exact source inventory, marker checks, documentation checks, and the existing full Maven build are proportionate now.

**Alternatives considered**:

- Add ArchUnit immediately: deferred until a real feature creates dependencies worth enforcing.
- Skip the build because markers do not compile: rejected because the Constitution requires a full check for this cross-service change.

