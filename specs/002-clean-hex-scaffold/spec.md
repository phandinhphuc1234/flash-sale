# Feature Specification: Clean Hexagonal Service Scaffold

**Feature Branch**: `002-clean-hex-scaffold`

**Created**: 2026-07-14

**Status**: Approved

**Input**: User description: "Use GitHub Spec Kit and the documented workflow to set up the project structure. Each service should follow the proposed flash-sale Clean Architecture and Hexagonal structure, preserve dependency direction adapter -> application -> domain and configuration -> adapter + application, and use business-capability ports rather than low-level technology ports. Do not create full classes in services; initialize structure only."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Navigate service architecture consistently (Priority: P1)

As a developer, I can open any service and see the same Clean/Hexagonal architectural zones, so later feature work has an obvious home without inventing a new local style.

**Why this priority**: A consistent service structure is the foundation for later business slices and reduces confusion in a microservice monorepo.

**Independent Test**: Inspect every service source tree and confirm that architecture zones exist without business implementation classes.

**Acceptance Scenarios**:

1. **Given** any service module, **When** a developer inspects its source tree, **Then** the service shows domain, application, adapter, and configuration ownership zones appropriate to that service profile.
2. **Given** the created scaffold, **When** production Java sources are inspected, **Then** no new controller, service implementation, entity, repository, port interface, event handler, or business policy class exists.

---

### User Story 2 - Preserve dependency direction before coding (Priority: P2)

As a maintainer, I can understand the allowed dependency direction before implementation begins, so future classes do not accidentally couple domain code to frameworks or adapters.

**Why this priority**: The package scaffold only helps if contributors understand how code is allowed to flow through it.

**Independent Test**: Read the architecture guide and feature contract, then confirm they describe adapter -> application -> domain and configuration -> adapter + application without allowing reverse dependencies.

**Acceptance Scenarios**:

1. **Given** the architecture guide, **When** a developer plans a future use case, **Then** they can identify where commands, use cases, ports, domain models, policies, and adapters belong.
2. **Given** a future port decision, **When** a developer compares it with the guide, **Then** technology-shaped ports such as RedisGetPort, RedisSetPort, or KafkaSendPort are rejected in favor of business-capability ports.

---

### User Story 3 - See the Spec Kit development flow (Priority: P3)

As a project owner, I can see how this scaffold was produced through the Spec Kit workflow, so I can repeat the same flow for later features.

**Why this priority**: The repository is adopting spec-driven development, so the process should be visible in the same change that creates the structure.

**Independent Test**: Read this feature's spec, plan, tasks, quickstart, and docs to confirm the workflow from specify to implement is represented.

**Acceptance Scenarios**:

1. **Given** the feature directory, **When** a developer reviews the artifacts, **Then** the feature contains spec, plan, research, data model, contract, quickstart, checklist, and tasks files.
2. **Given** the generated tasks, **When** implementation is complete, **Then** tasks are marked complete only for scaffold and documentation work actually performed.

### Edge Cases

- Existing application entry points and tests must remain unchanged.
- Empty package directories must be represented by marker files so the scaffold is visible in version control.
- Edge services may have fewer concrete future responsibilities, but the documented dependency direction still applies.
- No new production dependency, database migration, HTTP business contract, Kafka event, Redis script, or infrastructure deployment asset is introduced by this scaffold.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every service MUST have an initialized source structure that separates domain, application, adapter, and configuration concerns.
- **FR-002**: Core business services MUST include placeholders for domain models, value objects, policies, events, exceptions, application use cases, commands, results, inbound ports, outbound ports, inbound adapters, outbound adapters, and configuration.
- **FR-003**: The api-gateway scaffold MUST remain lean and must not imply service-owned persistence or business domain behavior that the gateway does not yet own.
- **FR-004**: The flashsale-service scaffold MUST include explicit placeholders for hot-path and reliability adapters such as Redis, messaging, outbox, and time integration without adding implementation logic.
- **FR-005**: Documentation MUST state the dependency direction as adapter -> application -> domain and configuration -> adapter + application.
- **FR-006**: Documentation MUST state that ports describe business capabilities, not vendor or API operations.
- **FR-007**: The scaffold MUST NOT introduce full production classes, controllers, service implementations, entities, repositories, port interfaces, Kafka consumers, Redis scripts, migrations, fake data, or business logic.
- **FR-008**: The feature MUST include Spec Kit artifacts showing the workflow used for this scaffold.
- **FR-009**: Existing service application classes, tests, POMs, and application configuration MUST remain behaviorally unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All nine service modules have visible architecture zones in version control.
- **SC-002**: Inspection finds zero new Java business implementation classes from this scaffold.
- **SC-003**: The architecture guide and feature contract both document dependency direction and business-capability port naming.
- **SC-004**: The feature directory contains the required Spec Kit artifacts for specification, planning, tasks, validation, and workflow explanation.
- **SC-005**: Existing Maven application context tests remain unaffected by the scaffold.

## Assumptions

- This feature initializes architectural folders only; real use cases will create concrete classes in future approved features.
- A marker file is acceptable to preserve otherwise-empty directories in version control.
- The scaffold records the current project convention but does not amend the Constitution into a stricter repo-wide Clean/Hexagonal law.
- No service boundary, database ownership rule, discovery rule, ingress rule, or event contract is changed.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: No persistence code, JPA entity, repository, schema, or shared business domain is introduced.
- **External ingress**: No gateway route, public API, or ingress policy is introduced.
- **API/event contracts**: No business HTTP or Kafka contract is added or changed; the feature contract documents scaffold rules only.
- **Durable and hot-path data**: No PostgreSQL schema, Redis Lua script, stock operation, or durable state is introduced.
- **Messaging reliability**: No producer, consumer, outbox worker, retry, or idempotency implementation is introduced.
- **Root infrastructure ownership**: No shared Docker, Kubernetes, Helm, Prometheus, Grafana, or alerting asset is added.
- **Observability**: Existing liveness, readiness, and Prometheus auto-configuration remains unchanged; tracing is documented as a future extension point but not implemented.
- **Verification**: Static scaffold inspection and existing Maven context tests apply; contract, integration, load, database, Kafka, Redis, and Kubernetes checks are omitted because no corresponding behavior is added.
- **Architecture decisions**: No ADR is required because this feature does not change service boundaries, persistence ownership, discovery, ingress, communication style, durability, or deployment coupling.
