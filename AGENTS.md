# Repository Instructions

## Required workflow

Before changing production code:

1. Read `.specify/memory/constitution.md`.
2. Read the active feature's `spec.md`, `plan.md`, and `tasks.md`.
3. Do not implement unresolved requirements.
4. Work on one approved task or one coherent task group at a time.
5. Run the required tests before marking a task complete.

## Repository architecture

- This is a Maven monorepo containing independent Spring Boot microservices.
- Java version is 21.
- Kubernetes Service and DNS provide service discovery.
- Eureka must not be introduced.
- Services must not access another service's database.
- JPA entities and repositories must never be shared between services.
- External traffic must enter through api-gateway.
- Kafka contracts must be versioned.
- PostgreSQL is the durable source of truth.
- Redis Lua is used only for atomic flash-sale hot-path operations.
- Synchronous service communication must use documented HTTP contracts.
- Kafka consumers must be idempotent, and versioned event contracts must be documented before
  implementation.
- Durable state changes followed by required event publication must use an outbox strategy where
  applicable.
- Every service must expose liveness, readiness, and Prometheus endpoints.
- Important requests and events must propagate a trace ID.

## Change control

- Do not introduce a new production dependency without updating the plan.
- Do not change service boundaries without an ADR.
- Do not change an API or event contract without updating its contract file.
- Do not report completion while required tests are failing.
- AI-generated code must follow the active approved specification, plan, and tasks.
- Unit, integration, contract, and load tests are required where applicable.
- Architectural changes require an ADR.

## Validation commands

- Full build: `./mvnw clean verify`
- Module build: `./mvnw -pl services/<service> -am verify`
- Kubernetes validation: `kubectl apply --dry-run=client -k <overlay>`
