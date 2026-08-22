# ADR 0023: Explicit Cloud Kafka Contract Provisioning

**Status**: Accepted

**Date**: 2026-08-22

## Context

The single-broker EKS environment is now reconciled by `flash-sale-cloud`. Kafka automatic topic
creation produced two one-partition topics even though accepted local contract tooling expects
three. Schema Registry has no subjects while service producers use
`auto.register.schemas=false`. The cloud therefore needs explicit, reviewable topic and schema
administration.

## Decision

- Argo CD owns the Kafka and Schema Registry workloads and disables broker automatic topic creation.
- Root infrastructure owns a versioned, explicit operator workflow for seven topics and nine
  TopicRecordNameStrategy subjects.
- Cloud topics use three partitions and RF1. Existing topics may expand only when record count is
  zero; they are never deleted or recreated.
- Registry subjects use exact accepted Git schemas and `BACKWARD_TRANSITIVE` compatibility.
- Provisioning is validation-only by default, explicit on apply, merge-gated, idempotent, and
  forward-recoverable.
- No topic, schema version, subject, message, or consumer group is deleted as rollback.

## Alternatives considered

- **Strimzi or another Kafka operator**: rejected because CRDs, controllers, and upgrades are
  excessive for a one-broker internship environment.
- **Argo sync hook Jobs**: rejected because they can rerun during unrelated syncs and make Registry
  availability a full-application reconciliation gate.
- **Service auto-creation/registration**: rejected because it hides ownership and already caused
  partition drift.
- **Keep one cloud partition**: rejected by the repository owner in favor of consistency with local
  tooling and later consumer parallelism.
- **Delete and recreate drifted topics**: rejected because it risks message loss and destroys
  evidence.

## Consequences

- Operators run one additional reviewed Phase 20 workflow after merging desired state.
- Topic/schema state is not automatically pruned, by design.
- Future contract additions must update the accepted AVSC catalog, registration tooling, inventory,
  tests, and evidence before producers are enabled.
- A later move to multiple brokers or a Kafka operator requires a new ADR and migration plan.
