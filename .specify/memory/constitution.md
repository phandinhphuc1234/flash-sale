<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.1.0
- Modified principles:
  - I. Specification-Driven Delivery -> I. Specification- and Task-Driven Delivery
  - II. Independent Service Ownership -> III. Strict Service and Data Ownership
  - III. Controlled Service Communication -> IV. Platform-Native Discovery and Controlled Ingress
  - IV. Durable and Atomic Data Handling -> VII. Atomic Hot Path and Durable Truth
  - V. Versioned Contracts and Verified Changes -> V. Contract-First Communication
- Added principles:
  - II. Independently Deployable Java Services
  - VI. Reliable Event Processing
  - VIII. Production Observability
  - IX. Layered Verification
  - X. Architecture by Recorded Decision
- Added sections: none
- Removed sections: none
- Templates and guidance:
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/tasks-template.md
  - ✅ .specify/templates/constitution-template.md (reviewed; remains generic)
  - ✅ .specify/templates/commands/*.md (directory absent; no files to update)
  - ✅ AGENTS.md
  - ✅ README.md (reviewed; no conflicting guidance)
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
downstream processing. Plans and tests MUST identify the health signals, metrics, and trace paths
needed to diagnose normal operation and failure.

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

## Platform Constraints

- Production code MUST target Java 21 and build through the repository Maven wrapper.
- A new production dependency MUST be documented and justified in the active plan before use.
- Services MUST remain independently buildable, testable, deployable, and responsible for their
  own schema migrations.
- Kubernetes manifest or overlay changes MUST pass client-side dry-run validation.
- Complexity or exceptions that depart from this constitution MUST be recorded in the plan's
  Complexity Tracking table and resolved by an approved amendment or ADR as applicable.

## Development Workflow and Quality Gates

1. Read this constitution and the active `spec.md`, `plan.md`, and `tasks.md` before production work.
2. Resolve requirements and document HTTP and Kafka contracts before implementation.
3. Record service ownership, schema, Redis Lua, outbox, idempotency, observability, test, dependency,
   deployment, and ADR impacts in the plan.
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
requirements.

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

**Version**: 1.1.0 | **Ratified**: 2026-07-10 | **Last Amended**: 2026-07-10
