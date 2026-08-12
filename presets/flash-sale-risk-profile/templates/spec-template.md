# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`
**Created**: [DATE]
**Status**: Draft
**Owner**: [PRODUCT/DOMAIN OWNER]
**Reviewers**: [REQUIRED BUSINESS/TECHNICAL REVIEWERS]
**Input**: User description: "$ARGUMENTS"

> This template is for newly created, high-risk Flash Sale features. Preserve confirmed baseline
> behavior and describe only the approved delta. Never invent decisions involving money, stock,
> authorization, consistency, idempotency, retry, compensation, quota, TTL, or data retention.

## Problem and Scope *(mandatory)*

**Business problem**: [Who experiences what problem, and why it matters]

**Goal**: [Business outcome this feature must produce]

**In scope**:

- [Explicitly included behavior]

**Out of scope**:

- [Explicitly excluded behavior]

## Baseline References and Requirement Delta

<!--
  Link any product baseline, business requirement, domain glossary, use-case catalog, contract, or
  preceding feature that governs this work. If none exists, state that explicitly. Do not restate
  unchanged baseline requirements as if they were new.
-->

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [link/id] | [baseline behavior or vocabulary] | [added/changed/removed behavior, or none] |

## User Scenarios & Testing *(mandatory)*

<!--
  Order stories by business value. Every story must be independently demonstrable and traceable to
  acceptance scenarios. Add or remove story sections as needed; do not keep empty samples.
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe the user journey in business language]

**Why this priority**: [Value and urgency]

**Independent Test**: [How this story can be demonstrated without later stories]

**Use-case references**: [UC-..., or N/A with rationale]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action/event], **Then** [observable business outcome]
2. **Given** [failure or boundary state], **When** [action/event], **Then** [observable outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe the user journey in business language]

**Why this priority**: [Value and urgency]

**Independent Test**: [How this story can be demonstrated independently]

**Use-case references**: [UC-..., or N/A with rationale]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action/event], **Then** [observable business outcome]

### Edge Cases and Failure Outcomes

- [Boundary, duplicate, timeout, stale-data, partial-failure, or concurrency case]
- [Required user-visible and operator-visible outcome]

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST [specific, observable behavior].
- **FR-002**: System MUST [specific, observable behavior].
- **FR-003**: System MUST [specific, observable behavior].

### Non-Functional Requirements

- **NFR-001**: [Measurable latency, throughput, availability, recovery, audit, or security target].
- **NFR-002**: [Measurable correctness or operability target].

### Business Rules and Invariants

- **INV-001**: [Rule that must remain true before and after every relevant operation].
- **INV-002**: [Rule covering money, inventory, identity, authorization, or consistency].

### Key Entities and Domain Model Delta *(include when feature involves domain data)*

- **[Entity/Aggregate]**: [Business meaning, identity, lifecycle, and owning context; omit persistence details].
- **Added/changed domain terms**: [Canonical glossary terms and disallowed ambiguous aliases].
- **Changed states or transitions**: [From → event/command → to, including forbidden transitions].

## Distributed-System Risk Decisions *(mandatory)*

Use `N/A` only with a concrete rationale. Unresolved high-impact choices belong in **Human Decisions
Required**, not in assumptions.

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | [amount source, authorization, duplicate/callback/refund behavior, or N/A] | [FR/INV/AC] |
| Inventory/oversell | [source of truth, reservation/deduction/release behavior, or N/A] | [FR/INV/AC] |
| Concurrency | [conflicting operations and required winner/outcome, or N/A] | [FR/INV/AC] |
| Idempotency/deduplication | [identity, scope, lifetime, replay outcome, or N/A] | [FR/INV/AC] |
| Consistency/ordering | [required consistency, event ordering, stale-read tolerance, or N/A] | [FR/INV/AC] |
| Retry/timeout/compensation | [business-visible outcome after failure, or N/A] | [FR/INV/AC] |
| Security/authorization | [actor, permission, abuse/rate limit/audit behavior, or N/A] | [FR/INV/AC] |
| TTL/quota/retention | [value and expiry/exhaustion/deletion behavior, or N/A] | [FR/INV/AC] |

## Dependencies and Compatibility

- **Upstream dependencies**: [Services, actors, contracts, or N/A]
- **Downstream consumers**: [Services, clients, events, reports, or N/A]
- **Compatibility promise**: [Backward/forward compatibility and rollout expectation]

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: [Technology-agnostic, measurable user or business outcome].
- **SC-002**: [Measurable scale/reliability/correctness outcome].
- **SC-003**: [Measurable operational or support outcome].

## Assumptions

- [Low-impact assumption that is safe to validate later]

## Human Decisions Required

<!--
  Every unresolved material decision must use the canonical marker below in the relevant requirement
  and be summarized here. A feature cannot be approved while a blocking item remains unresolved.

  Canonical marker: [NEEDS CLARIFICATION: exact question; why it matters; decision owner]
-->

| Priority | Question | Options/trade-off | Owner | Decision deadline | Resolution |
|----------|----------|-------------------|-------|-------------------|------------|
| BLOCKING | [Question] | [Options and effects] | [Role/name] | [Date/milestone] | [OPEN or decision] |

## Constitutional Constraints *(mandatory)*

- **Service ownership**: [Owning service and confirmation that no other service database is accessed]
- **External ingress**: [api-gateway route impact, or N/A]
- **API/event contracts**: [Contracts added or changed, versioning/compatibility impact, or N/A]
- **Durable and hot-path data**: [PostgreSQL ownership and any Redis Lua atomic operation, or N/A]
- **Messaging reliability**: [Kafka idempotency, outbox, retry, ordering, recovery, or N/A]
- **Root infrastructure ownership**: [Shared root `infra/` impact; service-owned configuration and migration impact; ADR exception, or N/A]
- **Observability**: [Liveness/readiness, declarative Prometheus endpoint exposure, trace-ID propagation, or N/A]
- **Verification**: [Applicable unit, integration, contract, load, Maven, and Kubernetes checks; justify omissions]
- **Architecture decisions**: [Required ADR references, or N/A with rationale]

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| [DATE] | Initial draft | [name] | [name/role] | Draft |
