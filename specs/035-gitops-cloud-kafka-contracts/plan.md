# Implementation Plan: Cloud Kafka Contract Provisioning

**Branch**: `codex/gitops-phase20-kafka-contracts` | **Date**: 2026-08-22
**Spec**: `specs/035-gitops-cloud-kafka-contracts/spec.md`
**Status**: Approved

## Summary

Materialize the seven accepted application topics and nine accepted TopicRecordNameStrategy schema
subjects on `flash-sale-dev`. Argo CD continues to own the Kafka and Schema Registry workloads and
disables automatic topic creation. A root-owned PowerShell operator workflow validates by default,
uses the in-cluster Kafka CLI plus a localhost-only Registry port-forward for explicit apply, blocks
unapproved drift, and proves idempotence with a second apply.

## Technical Context

**Language/Version**: PowerShell 7, Kafka 4.0 broker CLI, Avro schemas consumed by Java 21 modules

**Primary Dependencies**: Existing `kubectl`, Kafka container tools, Schema Registry REST API,
Maven wrapper, and four root-owned schema registration scripts; no new production dependency

**Storage**: Existing Kafka EBS volume and Schema Registry `_schemas` topic; no database, Redis, or
Kubernetes Secret changes

**Testing**: PowerShell AST parsing, Avro contract-module Maven verify, Kustomize render/client
dry-run, live topic/subject checks, explicit apply, and repeated apply

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`, reconciled by
`flash-sale-cloud`

**Project Type**: Root infrastructure and operator automation

**Performance Goals**: Preflight or apply completes within 10 minutes and reports all 16 desired
identities (7 topics plus 9 subjects)

**Constraints**: Three partitions/RF1; increase existing topics only when record count is zero; no
delete/recreate; no retention change; localhost-only port-forward; Payment remains disabled

**Scale/Scope**: One broker, one Registry, seven application topics, seven AVSC sources, nine
topic-record subjects

## Constitution Check

- **Specification traceability**: PASS; FR-001 through FR-013 map to inventory, drift, safety,
  repeatability, merge gate, and evidence tasks.
- **Service ownership**: PASS; no service source, database, migration, persistence, or domain model
  changes.
- **Communication**: PASS; only already accepted versioned Kafka contracts are materialized.
- **Data and messaging**: PASS; existing producer keys, consumer idempotency, retries, DLTs, and
  outboxes remain unchanged. Partition expansion is allowed only by the approved zero-record rule.
- **Root infrastructure ownership**: PASS; broker administration remains under root `infra/`.
- **Observability**: PASS; bounded topic descriptions, subject identities, compatibility, and
  operator outcomes provide evidence without high-cardinality application metrics.
- **Contracts and dependencies**: PASS; accepted AVSC files remain canonical and no new dependency
  or event version is introduced.
- **Validation**: PASS; the contract Maven module, script parsing, Kustomize dry-run, live checks,
  and two apply runs cover the changed risk. Service/load/database tests are omitted because no
  service behavior, hot path, persistence, or public interface changes.
- **Architecture decision**: PASS; ADR 0023 records explicit root-owned administration and rejects
  a Kafka operator or Argo hook for this internship topology.

## Phase 0 Research

### Decision 1: Explicit operator workflow instead of a Kafka operator

Use `infra/scripts/gitops/phase20-kafka-contracts.ps1`. It runs Kafka administration through
`kubectl exec` and Registry administration through a temporary localhost-only port-forward.

**Rationale**: The environment has one broker and no Strimzi CRDs. Adding an operator introduces
controllers, CRDs, upgrades, and ownership complexity that is disproportionate to seven topics.
Argo hooks could rerun during unrelated syncs and block the entire application on Registry issues.

**Alternatives rejected**: Strimzi/Kafka operator; Argo PreSync/PostSync Jobs; service startup
auto-creation.

### Decision 2: Three partitions and RF1 with a zero-record expansion guard

The repository owner selected three partitions and RF1. Existing one-partition topics may be
expanded only when every partition end offset totals zero. A topic with records or an unexpected
larger partition/RF value is drift and blocks all mutation.

**Rationale**: This matches accepted local tooling while avoiding historical key-to-partition
ordering discontinuity. RF1 is required by the single-broker topology.

### Decision 3: Disable broker auto-creation declaratively

Set `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` in the cloud Kafka StatefulSet. Argo owns this broker
policy; the operator script owns the approved internal topic inventory.

**Rationale**: The current two topics were silently created with one partition, proving that broker
defaults cause drift.

### Decision 4: Exact schema identity verification

Registration scripts transform Git AVSC strings into the generated SpecificRecord form, look up the
exact schema under the expected subject, require it to be the latest version, and verify
`BACKWARD_TRANSITIVE` compatibility. Campaign gains `-CheckOnly`; Order gains the deferred
PurchaseAccepted DLT subject.

**Rationale**: Checking only namespace and record name cannot detect field/type/default drift.

### Decision 5: Forward-only, rerunnable recovery

Partial success is retained. The operator fixes the underlying availability or compatibility issue
and reruns the same idempotent command. No automated rollback deletes topics, subjects, versions, or
messages.

**Rationale**: Deletion would violate durable messaging safety and erase operational evidence.

See `research.md` for the complete inventory and alternatives.

## Phase 1 Design

### Desired inventory

`contracts/cloud-kafka-inventory.md` maps seven exact topics to nine exact subjects and accepted
AVSC paths. No candidate catalog topic is included.

### Broker policy

`infra/k8s/overlays/cloud/platform/kafka-statefulset.yaml` disables automatic topic creation. This
is the only Kubernetes desired-state change and therefore must pass cloud-overlay client dry-run.

### Schema registration tooling

The existing Campaign, Flash Sale, Order, and Payment registration scripts remain the source of
provider-specific schema transformation and registration behavior. They gain consistent check-only
and exact-latest verification. The Order script registers both `OrderCreatedV1` and the deferred
Order DLT binding for `PurchaseAcceptedV1`.

### Phase 20 runner

The runner resolves the repository root, checks the exact EKS/Argo owner, validates Kafka/Registry
readiness, and inspects live state. Default mode performs no mutation. `-Apply` additionally
requires the Phase 20 script and broker policy on `origin/develop`, expands only empty
one-partition topics, creates missing topics, registers schemas, and executes the same verifier. A
second apply proves idempotence.

### Compatibility and rollout

This feature adds no schema version or event semantic. Existing accepted schemas are registered
before Payment or later Saga consumers are enabled. `BACKWARD_TRANSITIVE` remains subject-local.
Service code continues using `auto.register.schemas=false`.

### Failure and rollback

- Missing topic/subject: validation reports it; explicit apply creates it.
- Existing topic with records and the wrong partition count: fail before any mutation.
- Wrong RF, unexpected extra partitions, incompatible/latest schema drift: fail without correction.
- Mid-run dependency failure: retain successful identities and rerun after recovery.
- Broker auto-create policy: revert the Git manifest only if operationally required; created
  contracts are intentionally retained.

### Security and Secrets

No credentials are required. Registry access binds only `127.0.0.1`, verifies the port-forward child
process, and cleans up that exact PID in `finally`. Secret objects and values are never queried.

## Verification Strategy

| Requirement | Static/contract | Kubernetes | Live integration |
| --- | --- | --- | --- |
| FR-001..004 inventory and schemas | AVSC module verify; script inventory checks | N/A | topic describe; exact Registry lookup/config/latest |
| FR-005 idempotence | parser/static checks | N/A | two consecutive apply runs |
| FR-006..007 drift safety | scripted negative guards | N/A | zero-offset gate and no destructive commands |
| FR-008..010 operator safety | PowerShell parser | cloud render/client dry-run | validation-only, merge gate, port/PID cleanup |
| FR-011..012 boundaries | static review | rendered config | no Secret access; Payment flags unchanged |
| FR-013 evidence | `validation.md` | Argo status | counts, IDs/versions, compatibility, rerun output |

## Project Structure

```text
docs/adr/0023-cloud-kafka-contract-provisioning.md
infra/docker/schema-registry/register-campaign-schemas.ps1
infra/docker/schema-registry/register-flashsale-schemas.ps1
infra/docker/schema-registry/register-order-schemas.ps1
infra/docker/schema-registry/register-payment-schemas.ps1
infra/k8s/overlays/cloud/platform/kafka-statefulset.yaml
infra/scripts/gitops/phase20-kafka-contracts.ps1
infra/scripts/gitops/README.md
specs/035-gitops-cloud-kafka-contracts/
```

## Post-Design Constitution Check

PASS. The plan materializes accepted contracts without changing service or event semantics, keeps
shared administration under root infrastructure, adds no dependency, refuses destructive drift
repair, and defines exact validation/evidence gates.

## Complexity Tracking

No constitutional departure is required.
