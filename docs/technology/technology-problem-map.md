# Technology Problem Map

This document maps each technology to the problem it solves in the Flash Sale Engine. It is deliberately status-aware: do not treat a planned technology as already implemented.

## Quick Matrix

| Problem | Technology | Status | Owner location | Guardrail |
|---|---|---:|---|---|
| Build many services from one repo | Maven multi-module, Maven Wrapper | Current | `pom.xml`, `.mvn/`, `services/*/pom.xml` | One root wrapper only; no service-local wrappers |
| Service runtime | Java 21, Spring Boot 3.x | Current | all `services/*` | Services stay independently buildable |
| Service image builds | Dockerfile multi-stage builds | Current | `services/*/Dockerfile` | Build one image per service; no service-local Compose topology |
| Local container orchestration | Docker Compose | Current local dev | `infra/docker/` | No `compose.prod.yml`; Kubernetes is production target |
| Public entry point and routing | Spring Cloud Gateway WebFlux | Current shell, routes planned | `services/api-gateway` | Public traffic enters through gateway only |
| Simple HTTP service runtime | Spring Web | Current shell | non-gateway services | No cross-service DB calls |
| Pre-order Cart ownership boundary | Independent Spring Boot service | Current shell, behavior deferred | `services/cart-service` | No Cart API, gateway route, business schema, Redis/Kafka integration, or inter-service client is approved; see [ADR 0002](../adr/0002-cart-service-boundary.md) |
| Health and metrics endpoints | Spring Boot Actuator, Micrometer Prometheus | Current | service POMs and `application.yml` | Auto-configuration only; no manual `PrometheusMeterRegistry` code |
| Service discovery in cluster | Kubernetes Service + DNS | Planned | future `infra/k8s/` | No Eureka |
| Durable business truth | PostgreSQL per service | Planned | future service-owned migrations | No shared database, JPA entity, or repository |
| Database schema migration | Liquibase | Current empty wiring; business changesets planned | `services/<service>/src/main/resources/db/changelog/` | Empty masters establish ownership; real migrations require an approved schema feature; see [Liquibase migration rules](liquibase-migration-rules.md) |
| Flash-sale hot path | Redis Lua | Planned | future `flashsale-service` adapter and scripts | Redis is not durable truth; Lua only for atomic hot-path decisions |
| Async workflows | Kafka event bus | Planned | future contracts and adapters | Versioned event contracts, idempotent consumers, outbox where needed |
| Reliable state change plus event | Transactional outbox | Planned | future service-owned persistence adapter | State change and event publication must not drift |
| Synchronous internal calls | HTTP/REST contracts | Planned | `specs/<feature>/contracts/`, then accepted `contracts/openapi/` catalog when promoted | Contracts before clients; see [Service communication protocols](../architecture/service-communication-protocols.md) |
| Low-latency internal RPC | gRPC | Deferred | none yet | Requires measured need, plan, ADR, contracts, tests, and a Constitution amendment or clarification |
| Distributed trace instrumentation | Micrometer Tracing + OpenTelemetry bridge and OTLP exporter | Planned | future service POMs and `application.yml` | Application code uses Micrometer abstractions; W3C propagation; no direct OpenTelemetry SDK coupling in business or edge-policy code |
| Trace collection | OpenTelemetry Collector | Planned | future `infra/monitoring/otel-collector/` | Services emit OTLP; collector handles batching/retry/filtering |
| Metrics visualization | Prometheus + Grafana | Planned infrastructure, current scrape endpoints | future `infra/monitoring/` | Low-cardinality labels only |
| Distributed tracing backend | Tempo | Planned | future `infra/monitoring/tempo/` | Trace IDs must propagate through HTTP, Kafka, logs |
| Central logs | Loki with Grafana Alloy or OTel Collector | Planned | future `infra/monitoring/` | Do not introduce Promtail for new work; it is EOL |
| Architecture workflow | GitHub Spec Kit-style artifacts | Current docs workflow | `specs/*`, `.specify/` | Spec, plan, tasks before implementation |

## Current Technologies

### Java 21

Java 21 is the repository runtime baseline. It keeps every service on the same language level and enables one Maven reactor to verify all modules consistently.

### Maven Multi-Module

The root `pom.xml` owns the reactor and dependency/plugin management. Each service remains an independently buildable Maven module.

Use it for:

- one root build
- module-level verification
- consistent framework versions

Avoid:

- service-local Maven wrappers
- direct service-to-service module dependencies
- root runnable application code

### Dockerfiles and Docker Compose

Every service now has a service-local Dockerfile so the service can build its own image. Shared
local orchestration lives under `infra/docker/`.

Use it for:

- building one image per service
- running local PostgreSQL, Redis, and Kafka
- running the full local application topology through the `apps` profile
- debugging with `compose.dev.yml` when direct service ports are useful

Avoid:

- service-local Compose files
- `compose.prod.yml` as a production source of truth
- committing real `.env` secret values
- putting service business-schema migrations under `infra/docker/`

Detailed guidance lives in
[Container Compose and Kubernetes strategy](../deployment/container-compose-k8s-strategy.md).

### Spring Boot 3.x

Spring Boot is the service runtime baseline. Current services are application shells only, with context tests proving startup.

Use it for:

- independently runnable services
- application configuration
- Actuator auto-configuration

Avoid:

- business logic in framework configuration
- shared JPA/domain models between services

### Spring Cloud Gateway WebFlux

`api-gateway` uses Spring Cloud Gateway Server WebFlux as the edge service runtime. The repository has the gateway shell, but no real routes yet.

Use it later for:

- public routing
- JWT verification at ingress
- rate limiting
- request correlation and trace propagation

Avoid:

- business workflows in the gateway
- gateway-owned database
- introducing Eureka

### Actuator and Micrometer Prometheus

Every service currently exposes health/readiness/liveness/info/prometheus through declarative Spring Boot configuration. Spring Boot documents that the `prometheus` endpoint exposes scrapeable metrics and requires `micrometer-registry-prometheus`.

Use it for:

- Kubernetes probes
- Prometheus-compatible scrape endpoint
- basic service operability

Avoid:

- manual `PrometheusMeterRegistry` bean construction
- business code coupled to a registry implementation
- high-cardinality metric tags such as user ID, token, email, or order ID

## Planned Technologies

### Kubernetes Service and DNS

Kubernetes is the deployment target in the Constitution, but manifests are not implemented yet. Kubernetes DNS gives services stable names inside the cluster, which is why the repo prohibits Eureka.

Use it later for:

- service-to-service DNS names
- deployment, readiness, and liveness probes
- root-owned environment assets under `infra/`

Avoid:

- service discovery libraries that duplicate Kubernetes DNS
- service-owned shared deployment platform assets

### PostgreSQL Per Service

PostgreSQL is planned as durable truth. Each service owns its schema and migrations.

Use it later for:

- durable carts, orders, payments, campaigns, products, users, notifications, and chat history
- outbox tables where state changes must publish events

Avoid:

- one central database for all services
- direct queries into another service database
- shared JPA entity or repository modules

### Liquibase Migrations

Liquibase is already wired into each database-owning business-service shell with the runtime
dependency, an empty master changelog, and an empty `changes/` location. Actual business changesets
remain planned. The official Spring Boot integration can run changelog files from the application
at startup, but this repository must not add a first real migration until the owning service has an
approved schema feature.

Detailed rules live in [Liquibase migration rules](liquibase-migration-rules.md).

Use it later for:

- versioned schema changes per owning service
- repeatable local and CI database setup
- reviewable changelogs for tables, indexes, constraints, and outbox tables
- rollback planning when a feature requires a reversible database change

Expected future location:

```text
services/<service>/src/main/resources/db/changelog/
```

Recommended baseline shape for a future service:

```text
db/changelog/
├── db.changelog-master.yaml
└── changes/
    └── 001-create-<business-table>.yaml
```

Avoid:

- root-level shared migrations under `infra/`
- one migration module that owns every service schema
- adding a business changeset or table before an approved schema feature
- changing another service database from the current service's changelog

### Redis Lua

Redis is planned for atomic hot-path coordination in flash-sale flows. Lua matters because Redis guarantees atomic script execution on the server.

Use it later for:

- atomic quota reservation
- rate limiting
- short-lived token/session/catalog/campaign cache
- chat presence when appropriate

Avoid:

- treating Redis as durable truth
- placing long-lived business state only in Redis
- using Redis for non-hot-path logic just because it is fast

### Kafka

Kafka is planned for asynchronous workflows where services must react without tight synchronous coupling.

Use it later for:

- order requested
- order created or failed
- payment requested
- payment succeeded or failed
- notification requested
- campaign item prepared

Avoid:

- publishing events without a versioned contract
- non-idempotent consumers
- publishing required events outside an outbox when the event follows a durable state change

The selection rules for HTTP, Kafka, JWT validation, gRPC, WebSocket, Redis, and RabbitMQ are
defined in [Service communication protocols](../architecture/service-communication-protocols.md).

### Micrometer Tracing with OpenTelemetry

Distributed tracing is planned but is not installed in the current service runtime. The application
instrumentation standard is Micrometer Tracing, backed by the OpenTelemetry bridge and an OTLP
exporter. The OpenTelemetry Collector is a separate root-owned runtime component, not a Java library
embedded in each service.

```text
Spring observations and Micrometer Tracing
    -> micrometer-tracing-bridge-otel
    -> opentelemetry-exporter-otlp
    -> OpenTelemetry Collector
    -> Tempo or another approved backend
```

Use it later for:

- W3C `traceparent` and `tracestate` propagation across Gateway and service HTTP calls
- active trace/span correlation in structured logs and safe error responses
- OTLP export to one Collector endpoint rather than direct fan-out to several backends
- propagating trace context through Kafka with an approved versioned header contract

Avoid:

- calling the OpenTelemetry SDK directly from business or Gateway policy code when Micrometer's
  tracing abstraction provides the required capability
- treating the custom `X-Trace-Id` header as a replacement for W3C distributed trace context
- claiming tracing is active before the bridge, exporter, runtime properties, Collector, and trace
  propagation tests are implemented
- manually constructing tracer providers when Spring Boot auto-configuration covers the approved baseline

The current Gateway `X-Trace-Id`/UUID value is a compatibility correlation identifier. A later
tracing feature must prefer the active Micrometer trace context for canonical distributed trace IDs
and retain an explicit safe fallback for early failure paths where no span exists.

### OpenTelemetry Collector

OpenTelemetry Collector is planned as the central telemetry ingestion point. The official Collector docs describe it as vendor-agnostic infrastructure for receiving, processing, and exporting telemetry, and call out batching, retry, encryption, and filtering as reasons to use a collector beside services.

Use it later for:

- traces over OTLP
- metrics/logs routing where appropriate
- filtering sensitive data before exporting

Avoid:

- every service exporting directly to many backends
- putting PII, JWTs, passwords, or customer data in trace attributes or baggage

### Grafana, Tempo, Loki, and Log Collection

Grafana is planned as the operator UI. Tempo is planned for traces. Loki is planned for logs.

For new log collection work, prefer Grafana Alloy or OpenTelemetry Collector. Do not add Promtail as a new component: Grafana documents Promtail as end of life as of March 2, 2026.

Use this stack later for:

- service dashboards
- trace exploration
- log correlation by `trace_id`
- alerting on service and business signals

Avoid:

- log labels with high cardinality
- logging secrets or tokens
- using Promtail for new setup

## Deferred Technologies

### gRPC

gRPC appears in the reference architecture image, but it is not currently a repository baseline. The repo has no gRPC dependency, generated contract, or ADR.

Use only if a future plan proves it is needed for a specific internal call with clear benefits over HTTP/REST.

Before adopting gRPC, add:

- a measured requirement that the current HTTP baseline cannot satisfy
- a Constitution amendment or clarification permitting the selected use case
- ADR
- dependency decision
- `.proto` contract location
- compatibility/versioning rule
- test strategy

## Official References

- [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- [Spring Cloud Gateway Server WebFlux starter](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html)
- [Docker Compose multiple files](https://docs.docker.com/compose/how-tos/multiple-compose-files/)
- [Docker Compose profiles](https://docs.docker.com/compose/how-tos/profiles/)
- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Liquibase with Spring Boot](https://contribute.liquibase.com/extensions-integrations/directory/integration-docs/springboot/)
- [Kubernetes DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- [Redis Lua scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/)
- [Spring Boot 3.5 tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
- [Micrometer Tracing configuration](https://docs.micrometer.io/tracing/reference/configuring.html)
- [OpenTelemetry context propagation](https://opentelemetry.io/docs/concepts/context-propagation/)
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)
- [Grafana Loki Promtail EOL notice](https://grafana.com/docs/loki/latest/send-data/promtail/)
