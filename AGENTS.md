# Repository Instructions

## Required workflow

Before changing production code:

1. Read `.specify/memory/constitution.md`.
2. Read the active feature's `spec.md`, `plan.md`, and `tasks.md`.
3. Do not implement unresolved requirements.
4. Work on one approved task or one coherent task group at a time.
5. Run the required tests before marking a task complete.

## Spec-driven execution

- Read `docs/spec-driven-development/01-feature-spec-structure.md`,
  `docs/spec-driven-development/02-spec-kit-artifact-system.md`, and
  `docs/spec-driven-development/03-coding-constraints.md` when creating, planning, or implementing a
  feature.
- Confirm `SPECIFY_FEATURE_DIRECTORY` or `.specify/feature.json` points to the intended feature, and
  verify artifact approval status; file existence alone does not prove approval.
- `spec.md` owns WHAT and WHY, `plan.md` owns HOW, and `tasks.md` owns dependency-ordered execution.
  A plan or implementation must not introduce observable business behavior absent from the approved
  spec.
- Use `[NEEDS CLARIFICATION: ...]` for unresolved behavior and record decision priority, options,
  trade-offs, owner, and deadline under `Human Decisions Required` when the risk profile applies.
- Do not infer financial, security, correctness, stock, TTL, quota, refund, compensation,
  consistency, or idempotency semantics. Stop and request a decision when an approved canonical rule
  is absent.
- If implementation reveals a specification gap, stop the affected task; update and approve the
  spec, contracts, plan, and tasks as applicable before resuming.
- Record validation command, scope, result or exit status, and CI/PR reference where the plan
  requires evidence. A checked task is a completion ledger entry, not evidence by itself.
- Apply `flash-sale-risk-profile` and the `flash-sale-risk` workflow only to a newly created feature
  whose approved risk classification requires them. Do not retrofit completed feature artifacts.

## Repository architecture

- This is a Maven monorepo containing independent Spring Boot microservices.
- Java version is 21.
- Shared platform and environment assets belong under the root `infra/` directory:
  - `infra/docker/` for shared local backing-service orchestration.
  - `infra/k8s/` for Kubernetes bases and environment overlays.
  - `infra/helm/` for approved Helm deployment assets that do not duplicate Kustomize ownership.
  - `infra/monitoring/` for Prometheus scrape infrastructure, Grafana dashboards, and alert rules.
- Each service owns its application source, dependencies, runtime configuration, tests, and database
  migrations. Do not move these service-owned artifacts into root infrastructure.
- A service-specific image build recipe may remain with its service; shared container orchestration
  belongs under `infra/docker/`.
- Kubernetes Service and DNS provide service discovery.
- Eureka must not be introduced.
- Services must not access another service's database.
- JPA entities and repositories must never be shared between services.
- Services that own durable relational state in PostgreSQL SHOULD use Spring Data JPA as the
  default persistence technology.
- `JdbcTemplate`, JDBC, or native SQL MAY be used inside `adapter/out/persistence` for read
  projections, bulk operations, or database-specific performance needs; those types and queries
  MUST NOT leak into domain or application layers.
- Stateless edge services such as `api-gateway` MUST NOT add JPA merely to make service stacks
  look uniform.
- External traffic must enter through api-gateway.
- Kafka contracts must be versioned.
- PostgreSQL is the durable source of truth.
- Redis Lua is used only for atomic flash-sale hot-path operations.
- Synchronous service communication must use documented HTTP contracts.
- Kafka consumers must be idempotent, and versioned event contracts must be documented before
  implementation.
- Durable state changes followed by required event publication must use an outbox strategy where
  applicable.
- Every service must expose liveness, readiness, and Prometheus endpoints through Spring Boot 3.x
  Actuator auto-configuration, a runtime Prometheus registry dependency, and declarative
  `application.yml` configuration.
- Do not construct a `PrometheusMeterRegistry` in Java or couple business code to a Prometheus
  registry implementation unless an approved plan and ADR explicitly require it.
- Important requests and events must propagate a trace ID.

## Change control

- Do not introduce a new production dependency without updating the plan.
- Do not change service boundaries without an ADR.
- Do not change an API or event contract without updating its contract file.
- Do not place shared Docker orchestration, Kubernetes, Helm, or monitoring assets inside a service
  module. Any exception requires plan justification and an ADR.
- Do not report completion while required tests are failing.
- AI-generated code must follow the active approved specification, plan, and tasks.
- Unit, integration, contract, and load tests are required where applicable.
- Architectural changes require an ADR.

## Validation commands

- Full build: `./mvnw clean verify`
- Module build: `./mvnw -pl services/<service> -am verify`
- Kubernetes validation: `kubectl apply --dry-run=client -k <overlay>`
