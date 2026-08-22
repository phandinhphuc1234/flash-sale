# Research: Cloud Kafka Contract Provisioning

## Current live state

- Kafka and Schema Registry each have one ready replica.
- Only `campaign.lifecycle.v1` and `flashsale.purchase.events.v1` exist.
- Both existing topics have one partition, RF1, and total end offset zero.
- Schema Registry has zero subjects.
- Broker automatic topic creation is not disabled and produced the one-partition drift.

## Accepted inventory

The accepted catalog contains seven application topics, seven AVSC files, and nine topic-record
subject bindings. Candidate catalog topics are excluded. See `contracts/cloud-kafka-inventory.md`.

## Decisions

### Root-owned explicit provisioning

Use a versioned operator script, `kubectl exec` for Kafka, and localhost-only Registry forwarding.
Do not introduce Strimzi, a second controller, or Argo sync hooks.

### Cloud topic topology

The repository owner selected three partitions and RF1. The two existing one-partition topics are
empty, so a guarded expansion does not split historical key ordering. Any nonzero record count
blocks expansion.

### Broker auto-creation

Disable it through the Argo-owned Kafka StatefulSet. Approved topics must be explicit and verified.

### Schema equality

Each registrar must transform Git AVSC to the same SpecificRecord schema used by producers, perform
an exact subject lookup, require the expected schema to be latest, and require
`BACKWARD_TRANSITIVE`. Full-name-only checks are insufficient.

### DLT coverage

Order's PurchaseAccepted DLT binding was intentionally deferred by Feature 020 and is now included.
Payment's command DLT binding already exists in its registrar.

### Failure recovery

Keep successful forward changes and rerun. Topic/subject deletion is not a rollback mechanism.

## Alternatives considered

- **Keep one partition**: simpler, but rejected by the owner in favor of consistency with accepted
  local topology and future consumer parallelism.
- **Kafka operator/CRDs**: rejected as excessive for one broker and seven topics.
- **Argo sync hook Jobs**: rejected because unrelated syncs could rerun broker administration and a
  Registry outage could block the full application.
- **Service auto-registration/auto-creation**: rejected because it already caused topology drift and
  hides deployment order.
- **Delete/recreate drifted topics**: rejected due message-loss and audit risk.
- **Configure retention now**: rejected because no retention policy is approved; broker defaults
  remain unchanged.
