# Placement matrix

| Concern or class | Recommended package | Rule |
|---|---|---|
| Spring Boot application class | service root | Keep at the root package for component scanning. |
| Aggregate / domain entity | `<feature>.domain.model` | Pure Java; owns invariants and state transitions. |
| Value object | `<feature>.domain.model` or `.value` | Validate itself; no Spring/JPA dependency. |
| Domain event | `<feature>.domain.event` | Business fact, not Kafka payload. |
| Domain policy | `<feature>.domain.policy` | Business decision that does not naturally belong to one entity. |
| Domain service | `<feature>.domain.service` | Pure stateless domain behavior involving multiple domain objects; no persistence, broker, cache, or remote client. |
| Domain exception | `<feature>.domain.exception` | Business invariant failure; no HTTP status. |
| Inbound use-case interface | `<feature>.application.port.in` | Stable application capability; optional for trivial cases. |
| Outbound port | `<feature>.application.port.out` | Capability required by application, implemented outside. |
| Application service / use case | `<feature>.application.usecase` | Orchestrates domain and ports; owns transaction boundary. |
| Command / query | `<feature>.application.command` / `.query` | Transport-independent use-case input. |
| Result | `<feature>.application.result` | Transport-independent use-case output. |
| Page query/result | `<feature>.application.query` / `.result` | Use application-owned values; Spring Data `Pageable`/`Page` stay in persistence adapters. |
| Application exception | `<feature>.application.exception` | Use case cannot complete, not a framework error. |
| REST controller | `<feature>.adapter.in.web` | HTTP only: validation, authentication context extraction, mapping, response status. |
| Request DTO | `<feature>.adapter.in.web.request` | HTTP contract; Bean Validation may live here. |
| Response DTO | `<feature>.adapter.in.web.response` | HTTP contract; never return JPA entities. |
| Web MapStruct mapper | `<feature>.adapter.in.web.mapper` | Maps request ↔ command and result ↔ response. |
| Feature API error code | `<feature>.adapter.in.web.error` | Maps feature failures to stable external error codes. |
| Global exception handler | established `websupport.error` or feature `adapter.in.web.error` | Global within one service; translates exceptions to HTTP. |
| Kafka consumer | `<feature>.adapter.in.messaging` | Inbound adapter; maps event contract to use-case input. |
| Kafka publisher | `<feature>.adapter.out.messaging.kafka` | Outbound adapter behind an application port. |
| Atomic state plus outbox port | `<feature>.application.port.out` | Express required aggregate-state and outbox persistence as one capability. |
| JPA entity | `<feature>.adapter.out.persistence.jpa.entity` | Database representation; must not escape the adapter. |
| Spring Data repository | `<feature>.adapter.out.persistence.jpa.repository` | Framework-specific persistence detail. |
| Persistence mapper | `<feature>.adapter.out.persistence.jpa.mapper` | Maps domain ↔ JPA representation. |
| Persistence adapter | `<feature>.adapter.out.persistence.jpa` | Implements load/save ports and translates persistence errors. |
| HTTP/Feign/WebClient client | `<feature>.adapter.out.client.<capability>` | Implements an application-defined client port. |
| Client DTOs | Same outbound client adapter | Treat remote contracts as adapter details. |
| Security configuration | established service `security` package | Resource-server/filter configuration; outside domain. |
| Current actor abstraction | service `security` plus application-friendly value | Extract JWT in adapter, pass actor/user ID to use case. |
| Structured logging / correlation | service `observability` package | Technical cross-cutting behavior. |
| Jackson/JPA/clock configuration | service `configuration` package | Framework wiring and service-wide technical configuration. |
| Feature module wiring | service `configuration` or optional `bootstrap` | Prefer the established wiring package; add bootstrap only for explicit module assembly. |
| Liquibase/Flyway migration | `src/main/resources/db/...` | Owned by the service/database, not a Java feature package. |
| ArchUnit test | `src/test/.../architecture` | Enforces dependency boundaries. |

## Error handling boundaries

- Domain exception: invariant violation.
- Application exception: use-case failure such as not found, duplicate request, or downstream capability failure.
- Infrastructure exception: catch and translate inside the outbound adapter; do not leak `PSQLException`, Hibernate, Feign, or driver exceptions to controllers.
- API handler: map known failures to status, error code, message, path, trace ID, and field violations.
- A feature owns its API error codes; one service-level handler may dispatch those errors into the
  common response envelope. Do not move business exceptions into `shared` merely because one handler
  catches them.

## Mapper boundaries

Use separate mappers:

```text
Web boundary         → <Feature>WebMapper
Persistence boundary → <Feature>PersistenceMapper
Kafka boundary       → <Feature>EventMapper
Remote client        → <Capability>ClientMapper
```

Never build a `shared.mapper.GlobalMapper` that understands all representations.

## Service-wide technical boundary

Allowed in the established `websupport`, `security`, `observability`, and `configuration` packages:

- API envelope and field violation format.
- Security framework configuration.
- Correlation ID, tracing, metrics, logging conventions.
- Jackson, clock, serialization, and framework configuration.
- Service-local translation/wiring around a repository-level technical envelope.

Not allowed in service-wide `shared`:

- Domain entities or statuses.
- Feature repositories.
- Business services and policies.
- Feature-specific exceptions or mappers.

When a repository-level library already owns generic HTTP envelopes, a service must not define
competing copies. Shared source code is a technical contract, not a shortcut for sharing business
ownership.
