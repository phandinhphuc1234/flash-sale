---
name: apply-clean-hex-architecture
description: Apply this repository's DDD, Clean Architecture, and Hexagonal boundary rules when designing, planning, implementing, refactoring, or reviewing Spring Boot services. Use for package placement, ports and adapters, domain/application models, DTOs, mappers, exceptions, JPA, Kafka, schedulers, and external clients. Planning may use draft artifacts; production-code changes require approved governing Spec Kit artifacts.
---

# Apply Clean/Hex Architecture

Keep business meaning in the core and framework details at adapters. Use the skill to shape or
review design artifacts at draft time; require approved artifacts before changing production code.

## Load repository context

Before changing production code:

1. Read `.specify/memory/constitution.md` and `AGENTS.md`.
2. Resolve the active feature from `SPECIFY_FEATURE_DIRECTORY` or `.specify/feature.json`.
3. Read its `spec.md`, `plan.md`, `tasks.md`, and relevant contracts. Check their approval status
   before deciding whether the turn may design only or may also change production code.
4. Read `docs/architecture/ddd-clean-hexagonal-quick-reference.md` completely.
5. Read `docs/architecture/service-clean-hex-structure.md` when adding/moving packages, boundary
   models, persistence, messaging, scheduling, or external integrations.

If behavior is unresolved or artifacts are missing/unapproved, stop implementation and route the
work through the appropriate Spec Kit step. Do not invent business, consistency, idempotency,
security, financial, stock, retry, compensation, quota, or TTL semantics.

## Apply the boundary workflow

For each approved task or coherent task group:

1. Name the owning business capability and Aggregate, if one exists.
2. Identify driving adapters, input ports, use-case orchestration, domain rules, output ports, and
   driven adapters involved in the flow.
3. Place each type by responsibility:
   - Business concept, invariant, Aggregate, Value Object, policy, Domain Event: `domain`.
   - Command/query/result, use case, orchestration, input/output port: `application`.
   - REST, Kafka listener, scheduler, batch trigger: `adapter/in`.
   - JPA, Kafka publisher, HTTP/gRPC client, Redis, object storage: `adapter/out`.
   - Spring bean and framework wiring: the service's established `configuration` or `config`
     package; do not create a parallel convention.
4. Keep dependencies inward: `adapter -> application -> domain`; configuration may wire adapters
   and application. Domain remains Java-only.
5. Implement only the selected approved task scope.
6. Run the validation required by `plan.md` and `tasks.md`; record command, scope, and result.

## Protect boundary models

- Do not treat `1 table = 1 domain model`.
- Keep HTTP request/response, application command/result, domain model, JPA entity/projection,
  Kafka payload, and external provider DTO separate when they cross different boundaries.
- Put each mapper beside its adapter. Do not create a global mapper or let MapStruct make business
  decisions.
- Keep read models under the owning application query capability when they are not rich domain
  objects.
- Group growing packages by capability/Aggregate; do not create global `model`, `exception`, `dto`,
  `mapper`, `utils`, or business `common` dumping grounds.
- Create packages on demand with their first real responsibility; do not add empty architecture.

## Translate failures at the owning boundary

- Domain failures express invariant violations and live near the owning Aggregate/capability.
- Application failures express unsuccessful use-case outcomes without HTTP/JPA/Kafka types.
- Inbound adapters translate inward failures to HTTP or messaging outcomes.
- Outbound adapters translate database, broker, network, and vendor failures before they leak
  inward.
- Do not create one exception class for every field-format validation; use transport validation,
  Value Objects, or a meaningful domain failure as appropriate.

## Protect asynchronous state changes

Before implementing a Kafka consumer/producer that changes durable state, require the approved
artifacts to define:

- versioned event contract location, producer/consumer ownership, topic, key, ordering, event ID,
  compatibility, and trace-header propagation;
- idempotency boundary plus stale, duplicate, concurrent, retry, acknowledgment, DLT, replay, and
  recovery behavior;
- one local transaction for consumer deduplication and state change;
- an atomic persistence capability when state plus dedup/outbox must commit together, rather than
  unrelated low-level save/publish ports;
- outbox handling when a committed durable change must publish another event.

Keep wire payload/listener under `adapter/in/messaging`, publisher payload/adapter under
`adapter/out/messaging`, broker/runtime policy under the established configuration package, and
contract artifacts at the location selected by the approved plan.

## Handle growth deliberately

- Split packages by capability/Aggregate before considering a new service.
- Change a service boundary only for an approved bounded-context/ownership decision and ADR, not
  because file or table counts increased.
- Prefer ArchUnit tests when package dependency rules begin to drift.
- Do not add Spring Modulith solely to organize folders; plan it only when a service has genuine
  functional modules that need explicit module APIs and verification.

## Complete the task

Before reporting completion, confirm:

- implementation matches the approved spec and contracts;
- no framework model leaked into application/domain;
- tests cover changed domain/use-case behavior and affected adapters at the planned level;
- API, event, migration, dependency, and ADR artifacts were updated when applicable;
- required Maven/module checks passed and evidence was recorded.

This skill complements `$speckit-implement`; it does not replace specify, clarify, plan, tasks,
human approval, or validation gates.
