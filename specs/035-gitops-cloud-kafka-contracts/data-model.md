# Data Model: Cloud Kafka Contract Provisioning

This feature has no relational or Redis data model. Its operator-visible state consists of three
contract concepts.

## TopicContract

- **Identity**: exact topic name.
- **Expected fields**: partition count `3`, replication factor `1`.
- **Lifecycle**: `Absent -> PresentVerified`; an empty one-partition topic may transition through
  `Expandable -> PresentVerified`.
- **Invalid states**: nonempty topic with a partition mismatch, more than three partitions, or RF
  other than one. Invalid state is `BlockedDrift` and is never auto-corrected destructively.

## SchemaSubjectContract

- **Identity**: topic plus fully-qualified Avro record name.
- **Expected fields**: canonical Git AVSC identity, latest version identity, schema ID/version, and
  `BACKWARD_TRANSITIVE` compatibility.
- **Lifecycle**: `Absent -> RegisteredVerified`; exact reruns remain `RegisteredVerified` without a
  new version.
- **Invalid states**: expected schema is absent, not latest, or uses a different compatibility mode.

## ProvisioningRun

- **Mode**: validation-only or explicit apply.
- **State**: `Preflight`, `Blocked`, `ApplyingTopics`, `ApplyingSchemas`, `Verifying`, `Succeeded`.
- **Failure guarantee**: no deletion; successful prior operations are retained for a later rerun.
- **Evidence**: command, exit result, seven topic descriptions, nine subject IDs/versions and
  compatibility, second-apply result, and Argo/Kafka/Registry health.
