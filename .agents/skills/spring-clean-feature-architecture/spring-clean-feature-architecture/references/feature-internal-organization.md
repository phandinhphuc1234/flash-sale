# Feature-internal organization

## Contents

- Domain packages
- Application packages
- Inbound and outbound adapters
- Mapper ownership
- Exception and error ownership
- Service-wide technical packages
- Cross-feature communication
- Enforcement and over-engineering guardrails
- Research basis

Use this reference when a user wants a detailed tree inside each business feature. The governing
shape is:

```text
service root -> business feature -> Clean/Hexagonal boundary -> responsibility/technology
```

For example:

```text
com.philia.flashsale.order
├── OrderServiceApplication.java
├── order/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── websupport/
├── security/
├── observability/
└── configuration/
```

The service package is the deployable bounded context. The inner `order` package is a navigable
feature/module. That repetition is reasonable when the service will gain sibling features or the
feature has a substantial internal structure.

## Domain packages

Create only the responsibilities the feature actually owns:

| Package | Create it when | Do not put here |
|---|---|---|
| `domain.model` | Aggregates, entities, value objects, statuses, IDs, and invariant-bearing state exist | JPA entities, HTTP DTOs, Kafka payloads |
| `domain.event` | The domain raises meaningful business facts | Kafka-specific records, topic/header configuration |
| `domain.exception` | A named business invariant can fail | HTTP status, `ResponseStatusException`, database/provider errors |
| `domain.policy` | A decision combines rules that do not naturally belong to one aggregate method | Request validation or orchestration |
| `domain.service` | Stateless domain behavior spans multiple domain objects | Repository access, remote calls, transaction control |

A domain service must remain pure. If a proposed service calls a database, broker, cache, or remote
API, it is application orchestration behind an output port rather than a domain service.

Keep two or three cohesive types directly under `domain` when nested packages would only add clicks.
Do not interpret one database table as one domain model or one feature.

## Application packages

| Package | Responsibility |
|---|---|
| `application.port.in` | Stable use-case capabilities called by web, messaging, scheduling, or another feature |
| `application.port.out` | Capabilities the use case requires from persistence, brokers, clocks, locks, caches, or remote services |
| `application.usecase` | Cohesive orchestration and transaction boundary, usually one class per meaningful use case or tightly related use-case family |
| `application.command` | Transport-independent intent to change state |
| `application.query` | Transport-independent read criteria, including paging and sorting values |
| `application.result` | Transport-independent output returned by a use case |
| `application.exception` | A use case cannot complete although no domain invariant itself was violated |

Do not create `UseCase` plus implementation interfaces mechanically. An inbound port is useful when
multiple adapters call it, it forms a feature API, or it improves testing and dependency direction.
An application service may be the callable API for a small feature.

Application code must not import controller DTOs, `ResponseEntity`, Spring Data `Page`/`Pageable`,
JPA repositories/entities, Kafka clients, Redis clients, or another feature's adapter. A persistence
adapter translates Spring paging into an application-owned page result.

## Inbound adapter packages

```text
adapter/in/
├── web/
│   ├── <Feature>Controller.java
│   ├── request/
│   ├── response/
│   ├── mapper/
│   └── error/
├── messaging/
└── scheduling/
```

- Put HTTP shape and Bean Validation in `request` and `response`.
- Map request to command/query and result to response in a web mapper.
- Keep feature-specific external error codes in `web/error`.
- Let one service-level HTTP handler format known feature failures consistently.
- Treat Kafka listeners and their versioned wire payloads as messaging inputs, not domain events.
- Let schedulers trigger use cases; schedulers do not own expiration or cleanup rules.

Keep DTOs directly beside a small controller when separate `request` and `response` packages would
contain only one obvious type each. Split them once navigation or name collisions justify it.

## Outbound adapter packages

```text
adapter/out/
├── persistence/jpa/
│   ├── entity/
│   ├── repository/
│   ├── mapper/
│   └── <Feature>PersistenceAdapter.java
├── messaging/kafka/
└── client/<remote-capability>/
```

- Keep JPA annotations, Spring Data interfaces, projections, database queries, and persistence
  exception translation inside `persistence`.
- Let the persistence adapter implement application-owned ports.
- Keep Kafka producer payloads and event mappers in the Kafka adapter; a domain event is not the wire
  contract.
- Keep HTTP/gRPC provider DTOs, authentication, timeouts, and client error translation in the client
  adapter.
- Name ports after capabilities (`SaveOrderPort`, `ReserveStockPort`), not vendors
  (`JpaSavePort`, `RedisGetPort`, `HttpPostPort`).

When committed state requires event delivery, model the atomic requirement explicitly:

```text
CreateOrderService
    -> PersistOrderWithOutboxPort
    <- OrderPersistenceAdapter writes Order + outbox row in one transaction
```

Publish the versioned Kafka message from a separate outbound outbox publisher. Do not treat
`SaveOrderPort` followed by a direct required `PublishOrderEventPort` as atomic.

Use a flatter `persistence/` package until separate entity, repository, mapper, and adapter groups
actually improve navigation.

## Mapper ownership

Use one mapper per boundary:

```text
HTTP request <-> application command/result  : <Feature>WebMapper
domain <-> JPA representation                : <Feature>PersistenceMapper
domain event <-> versioned Kafka payload     : <Feature>EventMapper
internal model <-> remote provider contract  : <Capability>ClientMapper
```

MapStruct may implement mechanical mapping, but it must not select business status, enforce an
invariant, or coordinate a use case. Do not create a global mapper shared by boundaries or features.

## Exception and error ownership

```text
domain exception
    -> application outcome/exception when translation is needed
    -> feature web error code
    -> service-level exception handler
    -> common HTTP error envelope
```

- Domain exceptions describe violated business rules and know nothing about HTTP.
- Application exceptions describe unsuccessful use-case outcomes such as missing targets,
  conflicting idempotency keys, or unavailable required capabilities.
- Outbound adapters catch storage/provider exceptions and translate them before they cross inward.
- API error codes remain with the feature that owns the contract.
- A global handler is global only inside one service; it must not become a monorepo-wide business
  error catalog.

## Service-wide technical packages

Follow the service's existing convention. In this repository the usual shape is:

```text
websupport/            # global formatter/handler and HTTP context
security/              # framework security extraction and current actor
observability/         # trace/log/metric boundary support
configuration/         # Jackson, clock, JPA, serialization, runtime wiring
```

Keep business statuses, feature exceptions, repositories, business mappers, and domain services out
of those packages. If the monorepo already provides generic envelopes through `libs/common-web`,
reuse them and keep only service-specific translation locally.

Do not add a parallel `shared/config/bootstrap` tree when `configuration` already owns wiring. Use
`bootstrap` only when explicit module assembly is materially clearer than the established
configuration package.

## Cross-feature communication

A feature may call another feature only through a small application-facing API/port or a local
application/domain event. It must not import the other feature's controller, persistence adapter,
JPA entity, or Spring Data repository. If two features constantly mutate the same aggregate and
cannot be explained independently, reconsider whether they are actually separate features.

Keep create, get, cancel, and archive use cases for the same Aggregate lifecycle inside one feature.
An endpoint, controller method, or application service is not automatically a new feature.

## Enforcement

Use ArchUnit once package boundaries stabilize to check:

- domain and application never depend on adapters;
- domain is free of Spring, JPA, Kafka, Redis, and MapStruct;
- adapters do not depend directly on sibling adapters;
- top-level business features are cycle-free;
- one feature cannot access another feature's internal adapter packages.

Spring Modulith is optional. Use it only when the service has genuine functional modules whose
public APIs and allowed dependencies need module-level verification. Package-by-feature does not by
itself justify adding the dependency.

## Over-engineering guardrail

Do not copy the whole tree into every feature. For each proposed package ask:

1. Which real responsibility belongs here now?
2. Which dependency boundary does it protect?
3. Which test becomes clearer because it exists?

If none has a concrete answer, omit the package until the feature grows.

## Research basis

- Spring Boot recommends keeping the application class in a root package above the application code,
  which makes the service root a natural composition and scanning boundary:
  <https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html>
- Spring Modulith treats direct subpackages of the application package as modules by default and can
  verify cycles, public API access, and allowed dependencies:
  <https://docs.spring.io/spring-modulith/reference/fundamentals.html>
  and <https://docs.spring.io/spring-modulith/reference/verification.html>.
- ArchUnit's onion architecture rules enforce that domain/application do not depend on adapters and
  that adapters do not depend directly on one another:
  <https://www.archunit.org/userguide/html/000_Index.html#_onion_architecture>.
- Cockburn's original Ports and Adapters description keeps use cases independent of input devices,
  databases, and other external technology:
  <https://alistair.cockburn.us/hexagonal-architecture>.
