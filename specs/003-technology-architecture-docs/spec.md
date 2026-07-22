# Feature Specification: Technology and Architecture Documentation

**Feature Branch**: `003-technology-architecture-docs`

**Created**: 2026-07-14

**Status**: Approved

**Input**: User description: "Add documentation for technologies used to solve system problems based on the current repository technologies, and create a folder for drawing the first overall architecture diagram."

## User Scenarios & Testing

### User Story 1 - Understand technology choices (Priority: P1)

As a developer, I can read one technology map and understand which technology solves which problem, so future features do not add tools casually.

**Independent Test**: Open the technology docs and confirm every listed technology has a problem, status, owner, and guardrail.

### User Story 2 - See the architecture before implementation (Priority: P2)

As an architect, I can open a diagram folder and see the current target architecture as source-controlled Mermaid diagrams, so the team can review structure before coding deeper infrastructure.

**Independent Test**: Open the diagram README and system overview diagram source, then confirm the diagram distinguishes implemented, planned, and deferred components.

## Requirements

- **FR-001**: Documentation MUST distinguish implemented technology from planned or deferred technology.
- **FR-002**: Documentation MUST map technologies to concrete flash-sale problems.
- **FR-003**: Documentation MUST state that Kafka, Redis, PostgreSQL, Kubernetes, OpenTelemetry, Loki, Tempo, and Grafana are planned architecture concerns unless implemented by a later feature.
- **FR-004**: Documentation MUST state that gRPC is not currently adopted and requires a future approved plan and ADR before use.
- **FR-005**: Diagram files MUST live under `docs/architecture/diagrams/`.
- **FR-006**: Diagram source MUST be editable text, preferably Mermaid, so it can evolve with the repository.
- **FR-007**: Documentation MUST avoid recommending Promtail as a new log collection component because Promtail is EOL as of March 2, 2026.
- **FR-008**: Documentation MUST include Liquibase as the planned service-owned database migration tool and must not move migrations into root infrastructure.

## Success Criteria

- **SC-001**: A developer can identify current versus planned technology status from the docs in under 5 minutes.
- **SC-002**: The architecture diagram folder contains a readable overview diagram and a README explaining how to update it.
- **SC-003**: The docs preserve current repo behavior: no production dependency, service code, API contract, event contract, migration, or infrastructure manifest is added.

## Constitutional Constraints

- **Service ownership**: No service source or schema ownership changes; Liquibase is documented as a future service-owned migration tool only.
- **External ingress**: No gateway route or ingress manifest changes.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL schema or Redis Lua script changes.
- **Messaging reliability**: No Kafka implementation changes.
- **Root infrastructure ownership**: No runtime infra asset changes; docs only.
- **Observability**: Docs may describe future observability direction without adding tracing or logging agents.
- **Verification**: Static document inspection applies; Maven is not required because no build input changes.
- **Architecture decisions**: No ADR required because this is documentation of current/planned architecture, not an architecture change.
