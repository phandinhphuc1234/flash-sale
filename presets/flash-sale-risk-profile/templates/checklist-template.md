# [CHECKLIST TYPE] Checklist: [FEATURE NAME]

**Purpose**: [Requirement/design quality gate this checklist evaluates]
**Created**: [DATE]
**Feature**: [Link to spec.md]
**Plan**: [Link to plan.md, or N/A when checking specification only]
**Risk level**: [overall risk from plan, or pending]

> This checklist evaluates the quality and completeness of requirements and design artifacts. It is
> not a substitute for implementation test evidence. `/speckit-checklist` MUST replace the samples
> below with feature-specific, objectively answerable items.

## Requirement Decisions

- [ ] CHK001 Every behavior maps to a user story, acceptance scenario, functional requirement, or invariant [Completeness]
- [ ] CHK002 Money, stock, authorization, consistency, idempotency, retry, compensation, quota, TTL, and retention choices are explicit or justified N/A [Completeness]
- [ ] CHK003 No blocking `[NEEDS CLARIFICATION: ...]` marker remains before approval [Clarity]
- [ ] CHK004 Success criteria are measurable and technology-agnostic [Measurability]
- [ ] CHK005 Baseline behavior and the feature delta are distinguishable [Traceability]

## Distributed Correctness

- [ ] CHK006 Duplicate, replay, concurrency, timeout, partial-failure, and recovery outcomes are specified where applicable [Coverage]
- [ ] CHK007 Source of truth and durable transaction boundaries are unambiguous [Clarity]
- [ ] CHK008 Idempotency identity, scope, lifetime, and replay result are defined where required [Completeness]
- [ ] CHK009 Ordering, retry limits, compensation, and reconciliation have owners and terminal outcomes [Coverage]

## Architecture and Ownership

- [ ] CHK010 Each capability and data set has exactly one owning context/service [Ownership]
- [ ] CHK011 The plan prevents cross-service database access and shared persistence/domain types [Constitution]
- [ ] CHK012 Affected domain, application, port, adapter, and configuration responsibilities have concrete paths [Clarity]
- [ ] CHK013 Shared platform assets remain under root `infra/`, while runtime configuration and migrations remain service-owned [Constitution]
- [ ] CHK014 Any boundary or constitutional exception links an approved ADR [Governance]

## Contracts, Data, and Compatibility

- [ ] CHK015 Every HTTP/event change identifies owner, consumers, version, and compatibility behavior [Coverage]
- [ ] CHK016 Schema/data migrations include forward compatibility, rollout order, and safe rollback/recovery [Recoverability]
- [ ] CHK017 PostgreSQL, Redis Lua, Kafka idempotency, and outbox usage match repository constraints [Constitution]

## Verification and Evidence

- [ ] CHK018 Every critical invariant and acceptance scenario maps to a planned verification [Traceability]
- [ ] CHK019 Test ordering is explicitly selected by risk; test-first is required only where approved [Consistency]
- [ ] CHK020 Unit, integration, contract, load, migration, Maven, and Kubernetes checks are included or omissions are justified [Coverage]
- [ ] CHK021 Commands, environments, expected evidence, and owners are executable rather than vague [Verifiability]
- [ ] CHK022 Liveness, readiness, declarative Prometheus exposure, trace propagation, logs, metrics, and operational recovery are addressed [Operability]

## Findings

| Item | Severity | Finding | Required artifact change | Owner | Status |
|------|----------|---------|--------------------------|-------|--------|
| [CHK...] | [BLOCKING/HIGH/MEDIUM/LOW] | [finding] | [spec/plan/tasks path and change] | [owner] | [OPEN/RESOLVED] |

## Notes

- Number generated items sequentially and cite the relevant artifact section.
- Mark an item `[x]` only after recording evidence or a concrete artifact reference.
- Resolve blocking/high findings in the governing artifact, then re-run the gate.
