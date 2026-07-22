# Service Clean/Hexagonal Structure

This repository uses independent Spring Boot services. New business work should keep dependencies pointing inward:

```text
adapter -> application -> domain
configuration -> adapter + application
```

The structure is a boundary map, not a requirement to create every possible package in advance.
Baseline packages may start as markers; real types are added by an approved feature and placed
according to the rules below.

## Canonical Baselines

The nine business services use this small common shape:

```text
com/philia/flashsale/<context>/
├── domain/
│   ├── model/
│   ├── valueobject/
│   ├── policy/
│   ├── event/
│   └── exception/
├── application/
│   ├── command/
│   ├── query/
│   ├── result/
│   ├── usecase/
│   └── port/
│       ├── in/
│       └── out/
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   └── messaging/
│   └── out/
│       ├── persistence/
│       ├── http/
│       └── messaging/
└── configuration/
```

`flashsale-service` also reserves `adapter/out/redis`, `adapter/out/outbox`, and
`adapter/out/time` because those boundaries are already part of the approved platform design.
This does not approve a Redis script, outbox schema, or implementation.

`api-gateway` is an edge service and uses the technical profile accepted in
[ADR 0003](../adr/0003-lean-api-gateway-package-structure.md):

```text
com/philia/flashsale/gateway/
├── ApiGatewayApplication.java
├── configuration/       # General WebFlux/client/runtime wiring
├── routing/             # Java route definitions when needed
├── filter/
│   ├── global/
│   └── route/
├── security/
├── error/
├── faulttolerance/
├── ratelimit/
└── observability/
```

The gateway does not keep empty `domain`, `application`, or `adapter` layers merely to match a
business service. Business workflows, persistence, and domain invariants remain in the owning
service. A later approved feature may introduce a core boundary only when the gateway gains a real
responsibility that cannot stay in an edge package.

## Spec Kit Flow Used For This Baseline

1. `specify`: Define user value, scope, constraints, and measurable success before touching a service tree.
2. `plan`: Convert the approved scope into structure decisions, Constitution checks, dependency direction, and validation strategy.
3. `tasks`: Create dependency-ordered work with exact paths.
4. `implement`: Touch only the approved scaffold paths and mark completed tasks.
5. `validate`: Inspect the tree and run Maven checks when the local environment allows it.
6. `converge`: Compare implementation with `spec.md`, `plan.md`, and `tasks.md` before declaring completion.

Feature 002 created the original marker-only scaffold. Feature 006 refined the business-service
baseline. ADR 0003 supersedes only the gateway portion with the lean edge profile above. Completed
feature artifacts remain unchanged as history; the living convention is this document, accepted
ADRs, and the active feature's approved artifacts.

## Package Zones

`domain` is the service-owned business core. It must not depend on Spring, JPA, Kafka, Redis, HTTP clients, another service module, or shared business models.

`application` orchestrates use cases. It owns commands, results, use cases, and ports. It may depend on `domain`, but it must not depend on adapters.

`adapter` contains inbound and outbound technology integration. Controllers, Kafka listeners, persistence adapters, Redis adapters, and HTTP clients belong here when a future feature actually needs them.

`configuration` wires Spring beans and framework configuration in business services. It may know
about adapters and application services, but domain code must not know about configuration. In the
gateway edge profile, general runtime wiring stays under `configuration`, while reactive security
wiring stays with the security responsibility under `security`.

## Placement Reference

Use this table to translate a traditional layered-monolith package into the owning Clean/Hexagonal
boundary. The paths are destinations for real types, not a command to create every nested folder.

| Concern | Place it here | Boundary rule |
|---------|---------------|---------------|
| REST controller in a business service | `adapter/in/web` | Convert transport input into an input-port call; do not implement business rules |
| Gateway filter | `gateway/filter/global` or `gateway/filter/route` | Keep reactive edge filtering separate from business-service adapters |
| HTTP request/response DTO | Beside its controller, optionally `adapter/in/web/dto` | It is an HTTP contract representation, not an application or domain object |
| HTTP mapper | Beside the web adapter, optionally `adapter/in/web/mapper` | Maps web DTOs to commands/queries and results to responses |
| Request shape/format validation | The owning inbound adapter | Domain invariants still protect the core after boundary validation |
| Kafka listener and wire payload | `adapter/in/messaging` | Keep versioned wire representation separate from domain events |
| Use-case input | `application/command` or `application/query` | A command expresses change intent; a query expresses read intent |
| Use-case output | `application/result` | Independent of HTTP status, Kafka schema, and JPA |
| Input/output port | `application/port/in` or `application/port/out` | Name a use case or required business capability, not a vendor API |
| Use-case orchestration | `application/usecase` | Coordinates domain and ports; does not depend on concrete adapters |
| Aggregate, value object, policy, domain event | The matching `domain` package | Pure service-owned business meaning and invariant protection |
| Domain exception | `domain/exception` | Represents an invariant or domain rule failure, not HTTP/JPA failure |
| Use-case failure without domain meaning | Beside the use case or create-on-demand `application/exception` | Must not contain HTTP, persistence, or vendor types |
| HTTP/Kafka error translation in a business service | The owning inbound adapter | Converts inward failures into transport-specific outcomes |
| Gateway HTTP/security error translation | `gateway/error` and `gateway/security` | Own the edge response shape and reactive security failures without introducing a business core |
| JPA entity, Spring Data repository, projection | Inside `adapter/out/persistence` | Never share it and never treat it as the domain model |
| Persistence mapper/adapter | Inside `adapter/out/persistence` | Implements an output port and contains storage-specific translation |
| Downstream HTTP/provider client and DTO | `adapter/out/http` or a provider-specific outbound adapter | Provider payload and client exceptions must not leak inward |
| Kafka publisher payload/mapper | `adapter/out/messaging` | Maps an approved integration contract at the messaging boundary |
| Scheduled or batch trigger | A create-on-demand inbound scheduling adapter | A scheduler triggers a use case; it does not own business policy |
| Spring Security filter/converter | Business-service edge adapter/configuration; `gateway/security` at the gateway | Business authorization policy may be inward; framework security types may not |
| Spring bean/client wiring | Business-service `configuration`; gateway `configuration` or owning technical package | Composition only; it must not decide business behavior |
| Liquibase changeset | `src/main/resources/db/changelog/changes` in the owning service | Introduced only by the feature that approves the schema change |

## Create-on-Demand Rule

Only the canonical baseline packages above are pre-created. The gateway technical markers in ADR
0003 are an explicitly approved edge baseline; their marker is removed when the first real type is
added. Add other nested packages such as `dto`,
`mapper`, `error`, `validation`, `security`, `scheduling`, `provider`, `projection`, or `websocket`
when an approved use case introduces the first real responsibility that belongs there.

For example, a placement rule saying that a web request belongs under `adapter/in/web/dto` does not
mean every service needs that empty folder today. This keeps the repository easy to navigate and
prevents a false promise that every service uses JPA, Kafka, Redis, scheduling, or the same security
mechanism.

Do not create placeholder Java types to hold a package open. Use a marker only when the package is
part of the approved baseline; otherwise create the package together with its first real type.

## Boundary Models and Mapping

Do not reuse one class across transport, application, domain, and persistence boundaries. A normal
HTTP flow should look like:

```text
WebRequest
  -> web mapper
  -> Command or Query
  -> Input Port / Use Case
  -> Domain
  -> Result
  -> web mapper
  -> WebResponse
```

The same separation applies to messaging and persistence:

```text
KafkaMessage -> messaging mapper -> Command -> Use Case
Domain <-> persistence mapper <-> JpaEntity
DomainEvent -> messaging mapper -> VersionedIntegrationEvent
```

Small mappings can use constructors or package-private mapping functions. Create a dedicated mapper
class or mapper package only when the mapping has enough behavior or reuse to justify it. A global
mapper package is not allowed because it becomes a coupling hub between unrelated boundaries.

## Validation and Error Translation

- Validate transport shape and syntax at the inbound adapter, for example required fields and text
  format in an HTTP request.
- Protect business invariants in domain models, value objects, and policies even when an adapter has
  already validated its input.
- Use domain exceptions only for domain meaning. Use application failures for use-case outcomes that
  are not domain invariants.
- Translate failures to HTTP status/error bodies or Kafka retry/dead-letter behavior inside the
  owning adapter.
- Do not expose `ResponseEntity`, servlet/reactive types, JPA exceptions, Kafka exceptions, or vendor
  SDK exceptions to `application` or `domain`.

## Persistence and External Integrations

The domain model and persistence model are separate even when their fields initially look similar.
JPA annotations, Spring Data repositories, database projections, and storage-specific identifiers
stay under `adapter/out/persistence`. Liquibase remains service-owned under that same service's
resources; root `infra/` may provision a database but must not own business-schema migrations.

An outbound HTTP, payment, email, object-storage, or other vendor integration implements a capability
port from `application/port/out`. Its request/response types, authentication mechanics, timeouts, and
vendor errors stay in that outbound adapter. A vendor name must not appear in a domain or capability
port merely because the first implementation uses that vendor.

## Tests Follow the Owning Boundary

Tests mirror the production package that owns the behavior:

```text
src/test/java/com/philia/flashsale/<context>/
├── domain/...                 # Pure invariant and policy unit tests
├── application/usecase/...   # Use-case tests with fake/mock output ports
├── adapter/in/...             # Web or messaging boundary/contract tests
├── adapter/out/...            # Persistence/client adapter tests
└── integration/...            # Service-owned cross-boundary scenarios when needed
```

Create these test packages together with real tests; empty test trees are not part of the baseline.
Repository-level load scenarios stay under `load-tests/`. HTTP/Kafka contract artifacts belong to
the feature/contract catalog selected by its approved plan. Test-support code may be shared only
when it is infrastructure-oriented and transfers no business ownership.

## Port Rule

Ports should name business capabilities, not vendor operations.

Good future examples:

```text
LoadActiveSalePort
ReserveQuotaPort
PersistReservationWithOutboxPort
PublishReservationEventPort
SendNotificationPort
```

Avoid future examples:

```text
RedisGetPort
RedisSetPort
KafkaSendPort
JpaSavePort
HttpPostPort
```

For reliable state change plus event publication, prefer one capability boundary that preserves atomicity, such as `PersistReservationWithOutboxPort`, over separate save and publish ports that can leave the system inconsistent.

## Service Profiles

`flashsale-service`, `order-service`, and `payment-service` are core risk services. They should use the full domain/application/adapter structure when real behavior is added.

`authentication-service`, `product-service`, `cart-service`, `campaign-service`,
`notification-service`, and `chatting-service` use the same dependency direction, but should only
fill packages that a real use case needs.

`api-gateway` is an edge service. Keep it lean: routing, filters, security, error translation,
fault tolerance, rate limiting, and observability belong in their technical edge packages;
business domain behavior belongs in the owning service. Do not recreate empty
`domain/application/adapter` layers solely for visual consistency.

## Common Anti-Patterns

Reject these shapes during review:

- Root-level `dto`, `mapper`, `entity`, `repository`, `service/impl`, `utils`, or business `common`
  packages inside a service.
- Sharing a JPA entity, repository, schema, or domain model between services.
- Using a domain type directly as an HTTP response, Kafka payload, or JPA entity.
- Creating `Service` plus `ServiceImpl` only to mirror a framework convention; introduce a port only
  at a real dependency boundary.
- Creating low-level ports such as `RedisGetPort`, `KafkaSendPort`, `JpaSavePort`, or `HttpPostPort`.
- Letting controller, persistence, Kafka, Redis, or vendor exceptions leak into the application core.
- Adding the same optional package to all services only to make their trees visually identical.

## What the Current Baseline Does Not Add

- No controllers
- No service implementations
- No JPA entities or repositories
- No port interfaces
- No Kafka consumers or producers
- No Redis Lua scripts
- No migrations
- No shared business library
- No new production dependency
- No Product controller, read use case, DTO, entity, repository, migration, gateway route, or `GET`
  endpoint; that work requires its own approved feature and contract

The normative reviewer contract for business-service placement is
[`specs/006-clean-architecture-baseline/contracts/package-placement.md`](../../specs/006-clean-architecture-baseline/contracts/package-placement.md).
The gateway-specific profile is governed by
[ADR 0003](../adr/0003-lean-api-gateway-package-structure.md).
