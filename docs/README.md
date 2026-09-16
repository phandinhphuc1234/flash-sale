# Documentation Map

Documentation is organized by authority and reader. Do not duplicate a payload, topic, or decision
across multiple files when a stable link is enough.

## Start by role

| Reader | Start here | Then read |
|---|---|---|
| New backend developer | [Root README](../README.md) | [Service catalog](../services/README.md), [architecture](architecture/README.md) |
| Frontend developer | [Frontend integration guide](api/frontend-integration-guide.md) | [HTTP catalog](api/README.md), local Swagger |
| Seckill/Saga reviewer | [End-to-end flow](architecture/flash-sale-end-to-end-flow.md) | [Kafka catalog](kafka/04-topic-message-catalog.md), service READMEs |
| Platform/GitOps engineer | [Infrastructure](../infra/README.md) | [deployment](deployment/README.md), [runbooks](runbooks/) |
| Feature implementer | [Spec-driven development](spec-driven-development/01-feature-spec-structure.md) | active `specs/<feature>/spec.md`, `plan.md`, `tasks.md` |

## Documentation authority

| Question | Source of truth |
|---|---|
| What/why must a feature do? | Approved `specs/<feature>/spec.md` |
| How is it designed and sequenced? | Approved feature `plan.md` and `tasks.md` |
| Which HTTP endpoints exist? | [`api/README.md`](api/README.md) |
| What payload should the frontend send? | [`api/frontend-integration-guide.md`](api/frontend-integration-guide.md) |
| Which Kafka schemas are built? | [`contracts/kafka-avro-contracts`](../contracts/kafka-avro-contracts/README.md) |
| Which topics/ownership rules apply? | [`kafka/04-topic-message-catalog.md`](kafka/04-topic-message-catalog.md) |
| Why was an architectural choice made? | [`adr/`](adr/) |
| What was actually tested? | The feature's dated `validation.md` |
| How is a service operated? | Its [`services/<service>/README.md`](../services/README.md) and a matching runbook |

## Sections

- [`api/`](api/README.md) — endpoint inventory, payloads, Swagger, frontend handoffs.
- [`architecture/`](architecture/README.md) — service boundaries, Clean/Hexagonal structure,
  outbox, Saga, and end-to-end diagrams.
- [`adr/`](adr/) — accepted architecture decisions.
- [`kafka/`](kafka/README.md) — Schema Registry, Avro governance, topics, reliability.
- [`database/`](database/) — selected service schema explanations.
- [`deployment/`](deployment/README.md) — local/cloud packaging and GitOps roadmap.
- [`runbooks/`](runbooks/) — recovery and operational procedures.
- [`technology/`](technology/README.md) — technology rationale and migration rules.
- [`testing/`](testing/) — shared testing notes; executable scenarios live with their owning
  modules or under `load-tests/`/`infra/docker/smoke/`.

## Status language

- **Implemented** means production source exists.
- **Verified** requires a recorded passing command for a specific commit and environment.
- **Configured** means desired state exists but is not proof of a live deployment.
- **Historical evidence** describes a previous environment and may not reflect current runtime.
- **Scaffold/candidate/deferred** must never be presented as working functionality.

The current live status summary is [`current-system-implementation.md`](current-system-implementation.md).
