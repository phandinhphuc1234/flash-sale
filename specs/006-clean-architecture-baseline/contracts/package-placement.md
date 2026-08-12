# Contract: Clean Architecture Package Placement

This is a reviewer-facing source-structure contract. It is not an HTTP, OpenAPI, Kafka, or database contract.

## Required Dependency Direction

```text
adapter -> application -> domain
configuration -> adapter + application
```

Reverse dependencies from domain to application/framework code or from application to concrete adapters are prohibited.

## Required Business-Service Baseline

Every business service keeps service-owned packages for:

```text
domain/{model,valueobject,policy,event,exception}
application/{command,query,result,usecase,port/in,port/out}
adapter/in/{web,messaging}
adapter/out/{persistence,http,messaging}
configuration
```

Service-specific adapters are permitted only when an approved use case needs them. The gateway follows its lean profile and is not required to have the business-service baseline.

## Placement Rules

| Concern | Owner and future location |
|---------|---------------------------|
| HTTP controller/filter | `adapter/in/web` |
| HTTP request/response DTO and HTTP mapper | Beside the owning web adapter, optionally under `adapter/in/web/dto` and `adapter/in/web/mapper` |
| Request shape/format validation | The owning inbound adapter; domain invariants are still enforced inward |
| Kafka listener payload/mapper | Beside the owning inbound messaging adapter |
| Use-case input/output | `application/command`, `application/query`, and `application/result` |
| Use-case orchestration | `application/usecase`; it depends on domain and ports, not adapters |
| Domain invariant and exception | `domain/model`, `domain/valueobject`, `domain/policy`, and `domain/exception` |
| Use-case failure without domain meaning | Beside the owning use case or a create-on-demand `application/exception` package |
| HTTP/Kafka error translation | The owning inbound adapter; framework error types never move inward |
| JPA entity, repository, projection, persistence mapper/adapter | Inside the owning service's `adapter/out/persistence` boundary |
| HTTP/provider DTO, mapper, and client adapter | Inside the owning outbound adapter, such as `adapter/out/http` or a provider-specific package |
| Scheduler or batch trigger | An inbound adapter created when a scheduled use case exists |
| Spring Security/filter configuration | Inbound edge adapter and/or `configuration`, never domain |
| Spring bean wiring | `configuration` |
| Tests | Mirror the owning production package; cross-boundary integration tests remain in the owning service |

## Create-on-Demand Rule

The placement table defines where a real type goes; it does not require every optional nested directory to exist. Create DTO, mapper, validation, security, scheduling, provider, projection, or error packages with the approved feature that introduces their first real responsibility.

## Prohibited Patterns

- Root-level business `dto`, `mapper`, `entity`, `repository`, `service/impl`, `utils`, or `common` packages
- Shared JPA entities, repositories, schemas, or domain models
- Domain types annotated for Spring, HTTP, JPA, Kafka, Redis, or vendor SDKs
- Application ports named after low-level vendor operations
- Reusing one type as HTTP DTO, Kafka payload, JPA entity, and domain model

## Feature Boundary

This feature creates no business type or behavior. In particular, it does not approve or implement a Product read API, product schema, Product service use case, or gateway route.
