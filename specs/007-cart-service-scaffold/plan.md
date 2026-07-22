# Implementation Plan: Cart Service Scaffold

**Branch**: `[007-cart-service-scaffold]` | **Date**: 2026-07-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-cart-service-scaffold/spec.md`

**Plan status**: Approved

## Summary

Add `cart-service` as the tenth independently buildable Spring Boot service and the ninth business service. The module follows the repository's marker-only Clean/Hexagonal baseline, standard declarative operational endpoints, empty Liquibase baseline, and service-local image recipe. Root-owned Compose provisions the service and an empty logical `cart_db`; living architecture and deployment documents record the accepted boundary. The feature deliberately creates no Cart API, route, domain behavior, persistence schema, Redis/Kafka integration, or service-to-service client.

## Technical Context

**Language/Version**: Java 21

**Framework**: Spring Boot 3.5.x inherited from the root parent; Spring MVC for the ordinary business-service shell

**Build**: Maven wrapper and root multi-module reactor

**Primary Dependencies**: `spring-boot-starter-web`, `spring-boot-starter-actuator`, runtime `micrometer-registry-prometheus`, `liquibase-core`, and test-scoped `spring-boot-starter-test`

**Dependency justification**: These are the already approved baseline dependencies used by ordinary business-service shells. Web provides the future servlet runtime and Actuator transport; Actuator and the runtime registry satisfy the operational contract through auto-configuration; Liquibase establishes service-owned migration wiring without a schema; the test starter supports the application-context test. No JPA, JDBC driver, Kafka, Redis, gRPC, WebSocket, service-module, or HTTP-client dependency is added.

**Storage**: Empty local logical PostgreSQL database `cart_db` and empty service-owned Liquibase master; no datasource, table, record, or durable behavior

**Communication**: No business HTTP or Kafka contract. The base topology exposes only container port `8080`; no API Gateway route is added.

**Testing**: Spring Boot context test, exact source/marker/dependency scope audits, module Maven verification, full reactor verification, base Compose render, development Compose render, and living-document consistency audit

**Target Platform**: Linux OCI container for local Compose now; Kubernetes-ready service naming and root-owned future manifests later

**Performance Goals**: N/A for business throughput; the service has no business request path

**Constraints**: Preserve existing ports and services; no speculative Cart behavior; no direct public port in base Compose; no manual Prometheus registry; no cross-service database access; no rewrite of completed features

**Scale/Scope**: One new service module, one new local logical database, one Compose service, one debug port, one ADR, and the living documentation affected by the topology change

## Risk Classification

| Dimension | Level | Evidence | Required mitigation/verification |
|-----------|-------|----------|----------------------------------|
| Money/payment | Low | No payment behavior, amount, or provider integration | Negative source and contract audit |
| Inventory/concurrency | Low | Cart shell does not reserve or decrement stock | Reject Redis, Lua, and stock behavior |
| Security/privacy | Low | No identity, customer data, or public endpoint | Reject route, DTO, and business storage |
| Distributed consistency | Low | No event, synchronous client, or state transition | Contract and dependency absence audit |
| Contract/compatibility | Low | Only existing Actuator operational endpoints | Verify no Cart business contract or gateway route |
| Migration/rollback | Medium | New service boundary, module, empty database, and topology | Accepted ADR, empty changelog audit, documented rollback |
| Load/operability | Medium | One more deployable/buildable process and metrics target | Context test, image recipe audit, Compose renders, full build |

**Overall risk**: Medium because a new service boundary increases operational surface even though the implementation contains no business behavior.

**Selected test ordering**: Approve the boundary and design first; implement the marker-only module and context test as one scaffold group; validate exact scope before local topology and documentation integration; finish with module, Compose, and full-reactor checks. Strict business TDD is not selected because there is no domain behavior, API, or schema to drive with a failing behavioral test.

## Constitution Check

*GATE result before research: PASS. Re-check after design: PASS.*

- **Specification traceability**: PASS. Module and package work maps to FR-001 through FR-008; local topology maps to FR-009 through FR-011; ADR and living docs map to FR-012 through FR-013; negative scope and verification map to FR-014 through FR-016.
- **Service ownership**: PASS. ADR 0002 establishes future Cart ownership. The service contains no data implementation and cannot access another service database.
- **Communication**: PASS. No public route or service client is introduced. Kubernetes Service/DNS name `cart-service` is reserved by topology; any future HTTP or Kafka interaction requires a documented contract.
- **Data and messaging**: PASS. PostgreSQL remains the intended durable store. `cart_db` is empty; no Redis, Lua, Kafka, idempotency, outbox, retry, ordering, or reconciliation behavior exists.
- **Root infrastructure ownership**: PASS. Compose, environment example, PostgreSQL bootstrap, and future Kubernetes topology documentation remain under root `infra/`; runtime config, Dockerfile, and changelog remain under the service.
- **Observability**: PASS. The module uses Actuator plus the runtime registry and declarative health/info/Prometheus exposure. No registry bean or trace implementation is added.
- **Contracts and dependencies**: PASS. Every production dependency is justified above and matches the existing business-service baseline. The only new contract artifact is a reviewer-facing scaffold contract, not a business API/event contract.
- **Validation**: PASS. Context, Maven, Compose-render, exact-scope, and documentation checks are required. Business unit/integration/contract/load, database migration execution, and Kubernetes dry-run are inapplicable because the feature adds no business behavior, datasource/schema, protocol, load target, or manifest.
- **Architecture decisions**: PASS. [ADR 0002](../../docs/adr/0002-cart-service-boundary.md) is accepted before production implementation and records ownership, alternatives, consequences, migration impact, and rollback.

## Architecture and Boundary Mapping

### Cart service baseline

```text
com.philia.flashsale.cart/
├── CartServiceApplication.java
├── domain/
│   ├── model/ valueobject/ policy/ event/ exception/
├── application/
│   ├── command/ query/ result/ usecase/
│   └── port/in/ port/out/
├── adapter/
│   ├── in/web/ in/messaging/
│   └── out/persistence/ out/http/ out/messaging/
└── configuration/
```

All leaf zones are empty `.gitkeep` markers. No nested DTO, mapper, entity, repository, client, security, scheduling, or messaging package is created until an approved use case requires it.

### Dependency direction

```text
adapter -> application -> domain
configuration -> adapter + application
```

### Ownership boundary

- `cart-service`: future pre-order cart intent and lifecycle only.
- `product-service`: authoritative catalog and product information.
- `flashsale-service`: atomic hot-path stock reservation and flash-sale purchase admission.
- `order-service`: durable order lifecycle.
- `payment-service`: payment processing.
- `authentication-service`: identity and token issuance.

The exact meaning of Cart state, item snapshots, price validation, expiration, anonymous ownership, merge, and checkout coordination is not chosen in this scaffold.

## Runtime and Migration Design

- Internal application/container port: `8080`.
- Optional development host mapping: `18089:8080`.
- Base Compose: service name/DNS `cart-service`, `apps` profile, no direct host port.
- Image: multi-stage Java 21 build from the monorepo root, non-root runtime user.
- Operational endpoints: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, and `/actuator/prometheus`.
- Migration wiring: `db/changelog/db.changelog-master.yaml` contains exactly `databaseChangeLog: []`; `changes/` contains only an empty marker.
- Local database provisioning: `CREATE DATABASE cart_db;` only. PostgreSQL initialization runs only for a new volume; existing volumes require an explicit non-destructive database creation step outside automated validation.
- Compose continues setting `LIQUIBASE_ENABLED=false` for shells without datasources. A future persistence feature must add a driver/datasource and a first reviewed changeset.

## Scope Protection

This feature must reject:

- Cart controller, endpoint, route, request/response DTO, mapper, command/query/result, use case, domain model, entity, repository, or table
- Cart OpenAPI/AsyncAPI/Kafka event definition or gateway route
- JPA, PostgreSQL driver, Kafka, Redis, gRPC, WebSocket, or another service-module dependency
- Redis keys/Lua scripts, price or stock rules, checkout orchestration, idempotency, outbox, or scheduled expiration
- updates to historical `specs/001-*` through `specs/006-*`

## Verification Strategy and Evidence

| Requirement/risk | Verification | Command or evidence | Expected result |
|------------------|-------------|---------------------|-----------------|
| FR-001-FR-004 | Reactor and exact source/marker audit | XML module count plus Java/marker allowlists | Ten service modules; two Cart Java sources; 17 empty architecture markers |
| FR-002/FR-014 | Dependency audit | Inspect Cart POM and reject persistence/messaging/service dependencies | Only the justified baseline dependencies |
| FR-005-FR-007 | Runtime/config/migration audit | Inspect `application.yml` and changelog inventory | Five operational endpoints configured; empty master; no changeset |
| FR-008 | Container recipe audit | Inspect service-local Dockerfile | Root-context build, Java 21, non-root runtime, port 8080 |
| FR-009-FR-011 | Compose renders | Base and development `docker compose config` | Cart appears once; base has no host port; development uses 18089 |
| FR-012-FR-013 | Governance and living-doc audit | ADR section/status check and document searches | Accepted ADR; current topology consistently includes Cart |
| FR-014-FR-015 | Negative scope audit | Search Cart sources, gateway, contracts, and migrations | No business behavior, route, schema, event, or existing-service change |
| FR-016 | Module regression | `.\mvnw.cmd -pl services/cart-service -am verify` | Cart context test passes |
| FR-016 | Cross-cutting regression | `.\mvnw.cmd clean verify` | All ten service context tests pass |

## Project Structure

### Documentation (this feature)

```text
specs/007-cart-service-scaffold/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── cart-service-scaffold.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Repository paths affected

```text
pom.xml
README.md
services/cart-service/
├── pom.xml
├── Dockerfile
└── src/{main,test}/...
infra/docker/{compose.yml,compose.dev.yml,.env.example,README.md}
infra/docker/postgres/init/01-create-databases.sql
infra/k8s/README.md
docs/adr/0002-cart-service-boundary.md
docs/architecture/{service-clean-hex-structure.md,diagrams/system-overview.md,diagrams/system-overview.mmd}
docs/deployment/container-compose-k8s-strategy.md
docs/technology/technology-problem-map.md
```

**Structure decision**: Add one ordinary business-service module using the canonical marker-only structure. Keep all service runtime and migration assets within the module, and integrate only shared local orchestration and provisioning under root `infra/`.

## Complexity Tracking

No constitutional violation or waiver is accepted. The operational cost of a new service is an intentional consequence approved by ADR 0002, not an exception to repository rules.
