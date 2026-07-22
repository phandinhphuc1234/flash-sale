# Implementation Plan: Maven Multi-Module Microservice Skeleton

**Branch**: `develop` | **Date**: 2026-07-11 | **Spec**: [spec.md](spec.md)

**Status**: Approved

**Input**: Feature specification from `specs/001-scaffold-maven-services/spec.md`

## Summary

Create a single Maven reactor with one root parent/aggregator and nine independently executable
Spring Boot service modules. The baseline is Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3,
JAR packaging, one root Maven Wrapper, framework-managed health probes, and Prometheus
auto-configuration. Each service contains only a main application class, declarative configuration,
and one context-load test; business and infrastructure implementation remain out of scope.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.16; Spring Cloud BOM 2025.0.3; Spring Cloud Gateway WebFlux;
Spring Web; Spring Boot Actuator; Micrometer Prometheus registry; Spring Boot Test

**Storage**: N/A — no persistence, cache, schema, or durable state

**Testing**: JUnit 5 through `spring-boot-starter-test`, with one `@SpringBootTest` context-load test
per service plus runtime operational-endpoint smoke checks

**Target Platform**: JVM 21 on Windows and Linux/container environments; executable JARs

**Project Type**: Maven multi-module monorepo containing nine independent web-service applications

**Performance Goals**: Each skeleton starts and reaches healthy liveness/readiness state within
60 seconds on the supported development environment

**Constraints**: One root wrapper; exactly nine child modules; no direct service-module dependencies;
default port 8080 with `SERVER_PORT` override; no business logic; no manual metrics configuration;
preserve existing repository content

**Scale/Scope**: Nine application modules, nine context tests, five uniform operational endpoints per
service, and no domain entities or business contracts

## Constitution Check

*GATE result before Phase 0 research: PASS. Re-check after Phase 1 design: PASS.*

- **Specification traceability**: The approved spec resolves all requirements. This plan maps FR-001
  through FR-013 to root build setup, module skeletons, tests, and endpoint validation. Production
  implementation remains blocked until `tasks.md` is generated.
- **Service ownership**: No data, JPA entity, repository, schema, or domain model is introduced. Each
  module is an independent executable JAR and has no direct dependency on another service.
- **Communication**: The gateway module is the only future external-ingress component, but this feature
  adds no routes. No service discovery, synchronous service client, Kafka contract, or event flow is
  introduced.
- **Data and messaging**: PostgreSQL, Redis, Kafka, outbox, idempotency, retry, ordering, and
  reconciliation are not applicable because this feature creates no state or messaging behavior.
- **Root infrastructure ownership**: No shared Docker orchestration, Kubernetes, Helm, Prometheus
  server, or Grafana asset is introduced. Service POM dependencies and `application.yml` remain
  service-owned runtime artifacts, so the Prometheus endpoint does not violate the root `infra/`
  boundary.
- **Observability**: Every service exposes health, liveness, readiness, information, and Prometheus
  endpoints using a runtime registry dependency and declarative Actuator auto-configuration. No
  service constructs a Prometheus registry in Java. Trace propagation is deferred because there are
  no important business requests or events in this skeleton.
- **Contracts and dependencies**: The operational endpoint contract is documented in
  `contracts/operational-endpoints.md`. All new dependencies are listed and justified below. There is
  no business HTTP or Kafka compatibility impact.
- **Validation**: Context tests, isolated module verification, full reactor verification, POM/dependency
  inspection, and runtime endpoint smoke checks apply. Database integration, business contract, load,
  and Kubernetes tests are omitted because no corresponding behavior or manifests are introduced.
- **Architecture decisions**: The scaffold materializes the service topology declared by the user and
  repository instructions without changing an existing boundary, discovery method, ingress policy,
  persistence decision, or communication style; no ADR is required.

## Dependency Decisions

| Dependency | Modules | Scope and justification |
|------------|---------|-------------------------|
| `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux` | `api-gateway` | Reactive gateway application runtime; no routes or filters yet |
| `org.springframework.boot:spring-boot-starter-web` | Eight non-gateway services | Minimal independently startable HTTP application runtime |
| `org.springframework.boot:spring-boot-starter-actuator` | All services | Health, info, liveness, readiness, and metrics infrastructure |
| `io.micrometer:micrometer-registry-prometheus` | All services | Runtime registry required for the constitutional Prometheus endpoint; auto-configured only |
| `org.springframework.boot:spring-boot-starter-test` | All services | Test-scoped JUnit 5 and Spring context verification |

The root imports `org.springframework.cloud:spring-cloud-dependencies:2025.0.3` for Cloud dependency
management and inherits `org.springframework.boot:spring-boot-starter-parent:3.5.16`. Child modules do
not declare framework versions. No service depends on another service.

## Build and Plugin Management

- Root coordinates: `com.philia.flashsale:flash-sale-engine:0.1.0-SNAPSHOT`, packaging `pom`.
- Root properties set Java 21, UTF-8 source/reporting encoding, and Spring Cloud 2025.0.3.
- Root `dependencyManagement` imports the Spring Cloud BOM.
- Root `pluginManagement` configures `spring-boot-maven-plugin` and `maven-compiler-plugin` with
  release 21 and parameter-name retention.
- Each child declares `spring-boot-maven-plugin` under `build.plugins` to create an executable JAR.
- The official root wrapper is generated with `mvn wrapper:wrapper -Dtype=bin`; the Apache-generated
  wrapper JAR is allowed, while child wrappers and fabricated binaries are prohibited.

## Project Structure

### Documentation (this feature)

```text
specs/001-scaffold-maven-services/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── operational-endpoints.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
.mvn/
└── wrapper/
    └── maven-wrapper.properties
mvnw
mvnw.cmd
pom.xml
services/
├── api-gateway/
├── authentication-service/
├── product-service/
├── campaign-service/
├── flashsale-service/
├── order-service/
├── payment-service/
├── notification-service/
└── chatting-service/
```

Each service directory contains:

```text
pom.xml
src/
├── main/
│   ├── java/<package-path>/<ApplicationClass>.java
│   └── resources/application.yml
└── test/
    └── java/<package-path>/<ApplicationClass>Tests.java
```

### Module Mapping

| Module | Java package | Main class |
|--------|--------------|------------|
| `api-gateway` | `com.philia.flashsale.gateway` | `ApiGatewayApplication` |
| `authentication-service` | `com.philia.flashsale.authentication` | `AuthenticationServiceApplication` |
| `product-service` | `com.philia.flashsale.product` | `ProductServiceApplication` |
| `campaign-service` | `com.philia.flashsale.campaign` | `CampaignServiceApplication` |
| `flashsale-service` | `com.philia.flashsale.flashsale` | `FlashsaleServiceApplication` |
| `order-service` | `com.philia.flashsale.order` | `OrderServiceApplication` |
| `payment-service` | `com.philia.flashsale.payment` | `PaymentServiceApplication` |
| `notification-service` | `com.philia.flashsale.notification` | `NotificationServiceApplication` |
| `chatting-service` | `com.philia.flashsale.chatting` | `ChattingServiceApplication` |

**Structure Decision**: Use the repository root as the sole Maven parent/aggregator and place all
independent application modules directly under `services/`. Do not create root application sources,
nested standalone projects, `libs/` modules, or shared business code. Shared platform assets remain
owned by root `infra/`; the service-side Actuator dependency and declarative endpoint configuration
remain with each service because they are part of its independently runnable artifact.

## Configuration Design

Every service uses its own `application.yml` with its module name, `${SERVER_PORT:8080}`, endpoint
exposure for `health`, `info`, and `prometheus`, and health probes enabled. Declarative `info.app.name`
configuration exposes the same module identity through `/actuator/info`. The Prometheus runtime
registry is discovered automatically; no Java configuration class is created.

## Verification Plan

1. Inspect root and child POMs and use Maven validation to confirm all nine modules and inherited
   versions.
2. Generate the official root Maven Wrapper and confirm there are no service-local wrappers.
3. Run `./mvnw -pl services/<service> -am verify` (Windows: `.\mvnw.cmd`) for each of the nine
   affected services.
4. Run `.\mvnw.cmd clean verify` from the root and require `BUILD SUCCESS` with nine passing tests.
5. Inspect dependency trees to confirm no service artifact depends on another service artifact and no
   prohibited dependency is present.
6. Start each service sequentially with a unique `SERVER_PORT`, require healthy readiness within 60
   seconds, and verify all paths in `contracts/operational-endpoints.md` return the expected response,
   including the service's configured identity from `/actuator/info`.
7. Do not run Kubernetes dry-run, business contract, database integration, Kafka/Redis, or load tests;
   this feature changes no corresponding artifacts or behavior.

## Complexity Tracking

No constitutional violation or architecture exception is required for this feature.
