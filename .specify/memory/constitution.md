<!--
Sync Impact Report
- Version change: 1.1.0 -> 1.2.0
- Modified principles:
  - VIII. Production Observability (clarified Spring Boot 3.x auto-configuration and ownership)
- Added principles:
  - XI. Repository-Level Infrastructure Ownership
- Added sections: none
- Removed sections: none
- Templates and guidance:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
  - ✅ .specify/templates/constitution-template.md (reviewed; remains generic)
  - ✅ .specify/templates/commands/*.md (directory absent; no files to update)
  - ✅ AGENTS.md
  - ✅ README.md
  - ✅ docs/adr/0001-root-infrastructure-ownership.md
  - ✅ infra/README.md and infra/*/README.md
  - ✅ specs/001-scaffold-maven-services/spec.md
  - ✅ specs/001-scaffold-maven-services/plan.md
  - ✅ specs/001-scaffold-maven-services/tasks.md (reviewed; no task change required)
- Follow-up TODOs: none
-->
# Flash Sale Constitution

## Core Principles

### I. Specification- and Task-Driven Delivery
Production changes, including AI-generated code, MUST follow the active feature's approved
`spec.md`, `plan.md`, and `tasks.md`. API and event contracts MUST be documented before their
implementation begins. Unresolved requirements MUST NOT be implemented, and work MUST proceed as
one approved task or coherent task group at a time. This preserves traceability and prevents tools
or contributors from inventing unapproved behavior.

### II. Independently Deployable Java Services
The system MUST remain a Maven multi-module monorepo using Java 21 and independently buildable and
deployable Spring Boot services. Each service module MUST support isolated build and verification
with its required upstream modules. A change MUST NOT couple service deployment lifecycles unless
an approved ADR explicitly changes the architecture.

### III. Strict Service and Data Ownership
Each service MUST own its database and schema. Cross-service database queries are prohibited.
Services MUST NOT share JPA entities, repositories, or business domain models. Shared artifacts MAY
contain infrastructure utilities or generated contract types only when they do not transfer domain
ownership. These boundaries preserve autonomy and prevent persistence coupling.

### IV. Platform-Native Discovery and Controlled Ingress
Kubernetes Service and DNS MUST be the only service discovery mechanism; Eureka MUST NOT be
introduced. All public traffic MUST enter through `api-gateway`. Internal service addresses MUST
use Kubernetes-provided names rather than a second discovery registry. This keeps topology native
to the deployment platform and provides a single policy enforcement point for public traffic.

### V. Contract-First Communication
Synchronous cross-service communication MUST use explicit, documented HTTP contracts.
Asynchronous communication MUST use documented, versioned Kafka contracts. Contract changes MUST
update their contract files before implementation and MUST include compatibility and contract-test
impact. Database access or shared domain code MUST NOT substitute for a service contract.

### VI. Reliable Event Processing
Kafka consumers MUST be idempotent so duplicate delivery cannot create duplicate business effects.
When a durable state change must be followed reliably by event publication, the design MUST use a
transactional outbox strategy unless the plan documents why atomic publication is not required.
Retry, deduplication, ordering, and failure-recovery behavior MUST be explicit in the plan.

### VII. Atomic Hot Path and Durable Truth
Flash-sale stock deduction MUST be atomic through Redis Lua. PostgreSQL MUST remain the durable
source of truth, while Redis MUST be limited to atomic hot-path coordination and MUST NOT become
the authoritative store. Plans MUST define persistence, reconciliation, expiration, and failure
recovery for any Redis-backed state.

### VIII. Production Observability
Every service MUST expose liveness, readiness, and Prometheus-compatible metrics endpoints. Every
important request and event MUST propagate a trace ID across gateway, HTTP, Kafka, logs, and
downstream processing. On Spring Boot 3.x, the Prometheus endpoint MUST use Actuator
auto-configuration, a runtime Prometheus registry dependency, and declarative application
configuration. Service code MUST NOT construct a `PrometheusMeterRegistry` or couple business logic
to a registry implementation unless an approved plan and ADR document the exception. Plans and
tests MUST identify the health signals, metrics, and trace paths needed to diagnose normal operation
and failure.

### IX. Layered Verification
Unit, integration, contract, and load tests MUST be included where applicable to the changed risk.
The plan MUST state which layers apply and justify any omitted layer. Required module, full-build,
contract, load, and Kubernetes validations MUST pass before completion is reported; a failing
required check prohibits task completion.

### X. Architecture by Recorded Decision
Architectural changes MUST include an ADR describing context, decision, alternatives, consequences,
and migration impact. Changes to service boundaries, discovery, ingress, persistence ownership,
communication style, durability, or deployment coupling are architectural. Implementation MUST
NOT begin until the ADR and corresponding plan change are approved.

### XI. Repository-Level Infrastructure Ownership
Shared platform and environment assets MUST live under the root `infra/` directory rather than
inside an individual service. This includes shared local backing-service orchestration, Kubernetes
bases and overlays, Helm deployment assets, Prometheus scrape infrastructure, Grafana dashboards,
and alert rules. Each service MUST retain ownership of its application source, dependencies,
runtime configuration, tests, and database migrations. A service-specific image build recipe MAY
remain with its service when required for independent builds, while shared container orchestration
belongs under `infra/docker/`. Infrastructure MAY provision isolated databases but MUST NOT own or
centralize service business-schema migrations. Exceptions MUST be justified in the active plan and
approved by ADR. This boundary keeps platform lifecycle concerns separate without weakening service
autonomy.

## Platform Constraints

- Production code MUST target Java 21 and build through the repository Maven wrapper.
- A new production dependency MUST be documented and justified in the active plan before use.
- Services MUST remain independently buildable, testable, deployable, and responsible for their
  own schema migrations.
- Shared Docker orchestration, Kubernetes, Helm, and monitoring assets MUST be placed under root
  `infra/`; service-owned runtime configuration and migrations MUST remain with the owning service.
- Kubernetes manifest or overlay changes MUST pass client-side dry-run validation.
- Complexity or exceptions that depart from this constitution MUST be recorded in the plan's
  Complexity Tracking table and resolved by an approved amendment or ADR as applicable.

## Development Workflow and Quality Gates

1. Read this constitution and the active `spec.md`, `plan.md`, and `tasks.md` before production work.
2. Resolve requirements and document HTTP and Kafka contracts before implementation.
3. Record service ownership, schema, Redis Lua, outbox, idempotency, observability, root
   infrastructure placement, test, dependency, deployment, and ADR impacts in the plan.
4. Generate dependency-ordered tasks with exact file paths and explicit verification work.
5. Implement only an approved task or coherent task group, regardless of whether code is written by
   a human or generated by AI.
6. Run `./mvnw -pl services/<service> -am verify` for affected modules and
   `./mvnw clean verify` for cross-cutting changes or whenever required by the plan.
7. Run applicable unit, integration, contract, and load tests.
8. Run `kubectl apply --dry-run=client -k <overlay>` for every affected Kubernetes overlay.
9. Record validation evidence; completion MUST NOT be reported while a required check fails.

Reviews MUST verify specification and task traceability, independent deployability, service and
schema ownership, discovery and ingress rules, contract documentation and versioning, consumer
idempotency, outbox decisions, stock atomicity, durable truth, observability, test coverage, and ADR
requirements. Reviews MUST also reject shared platform assets placed in service modules or
service-owned runtime configuration and migrations moved into root infrastructure without an
approved exception.

## Governance

This constitution supersedes conflicting repository practices and templates. Amendments MUST be
documented in this file with a Sync Impact Report, synchronized to dependent templates and runtime
guidance, and approved through the repository's normal review process. Architectural amendments
MUST include or reference the applicable ADR and migration implications.

Constitution versions follow semantic versioning: MAJOR for incompatible principle or governance
changes, MINOR for new principles or materially expanded obligations, and PATCH for clarifications
that do not change obligations. Every feature plan MUST perform the Constitution Check before Phase
0 research and again after Phase 1 design. Reviewers MUST reject unexplained violations; approved
exceptions MUST appear in Complexity Tracking with the rejected simpler alternative.

**Version**: 1.2.0 | **Ratified**: 2026-07-10 | **Last Amended**: 2026-07-11
