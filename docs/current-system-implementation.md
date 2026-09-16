# Current System Implementation

This is a source-oriented status summary, not a substitute for feature validation evidence.

| Field | Value |
|---|---|
| Snapshot date | 2026-09-08 |
| Java / Spring | Java 21 / Spring Boot 3.5.16 / Spring Cloud 2025.0.3 |
| Build | Maven Wrapper multi-module reactor |
| Environments | local Docker Compose and cloud AWS EKS only |
| Current live cloud | Stopped for cost control; recreation/live gates pending |

## Capability status

| Area | Source state | Verification boundary |
|---|---|---|
| API Gateway | Implemented routing, JWT, CORS/preflight, Redis rate limiting, trace/error handling, local OpenAPI aggregation | Module tests; local edge smoke |
| Authentication | Implemented shopper sessions, refresh rotation/revocation, JWKS, service client credentials, bootstrap path | Module/integration tests; local flows |
| Product | Implemented public/admin catalog, lifecycle/versioning, Campaign/Cart/Order internal decisions | Module/integration tests |
| Cart | Implemented persistent intent, versioned snapshot, Product enrichment, post-payment reconciliation | Module/integration tests and Feature 049 local flow |
| Campaign | Implemented draft/schedule/activate, Product/Inventory preparation, lifecycle outbox/recovery | Module/integration tests |
| Flash Sale | Implemented Redis Lua admission, Stream handoff, durable reservation/outbox, confirmation/release/expiry | Feature 044 tests and controlled load evidence |
| Inventory | Implemented stock/movements/allocations plus regular holds and Kafka finalization | Module/integration/concurrency tests |
| Order | Implemented query, Flash Sale Saga, normal Buy Now/Cart checkout, recovery/manual-review rules | Feature 044/049 local verification |
| Payment | Implemented payment aggregate, Stripe Checkout/webhook, deduplication, outbox, recovery | Module tests; historical Stripe cloud smoke |
| Notification | Scaffold only | Application context test only |
| Kafka contracts | 25 Avro files across implemented topic families, generated SpecificRecords, compatibility tests | Contract module verify |
| Kubernetes/GitOps | Kustomize desired state, migration Jobs, Argo Applications, ECR promotion workflow | Historically exercised; no current EKS cluster |
| Observability | Service Actuator metrics plus Prometheus/Grafana seckill baseline | Historically deployed; desired state remains in Git |

## Active runtime topology

The Maven reactor has ten Spring Boot applications. The implemented commerce topology is Gateway plus
Authentication, Product, Cart, Campaign, Flash Sale, Inventory, Order, and Payment. Notification is
not included in endpoint totals or the active image-promotion set because its business capability is
not implemented.

```text
external client
    -> API Gateway
       -> Auth / Product / Cart / Campaign / Flash Sale / Inventory / Order / Payment

service-owned PostgreSQL databases
Redis: Auth throttle + Gateway limiter + Flash Sale atomic hot path/Stream
Kafka + Schema Registry: lifecycle, purchase, payment, order, regular hold, Cart reconciliation
Stripe: Checkout API and signed webhook callback
```

## Implemented HTTP surface

The canonical inventory contains 50 unique method/path pairs:

- 39 Gateway-public endpoints (38 shopper/admin plus the Stripe webhook);
- 10 internal service endpoints;
- one JWKS identity-trust endpoint.

Read [`api/README.md`](api/README.md) for the inventory and
[`api/frontend-integration-guide.md`](api/frontend-integration-guide.md) for payloads. Swagger is a
local opt-in and remains disabled on the public cloud edge.

## Implemented asynchronous surface

The executable Avro module currently contains:

- Campaign scheduled/activated lifecycle facts;
- Flash Sale purchase accepted/confirmed/released facts and confirm/release commands;
- Payment requested command and succeeded/failed facts;
- Order lifecycle V1/V2 records plus manual-review fact;
- Inventory regular-hold confirm/release commands and confirmed/released/expired facts;
- Cart checkout reconciliation command.

See [`../contracts/kafka-avro-contracts/README.md`](../contracts/kafka-avro-contracts/README.md).
Candidate Product and Campaign end/cancellation families in older design documents are not runtime
contracts.

## Data and migration state

Authentication, Product, Cart, Campaign, Flash Sale, Inventory, Order, and Payment own separate
logical PostgreSQL databases and service-local Liquibase changelogs. Shared Compose/Kubernetes
creates infrastructure/logical databases; it does not own business tables.

Migration rules:

- run an explicit one-off local process or Kubernetes Job before normal application rollout;
- keep normal replicas with Liquibase disabled;
- use expand/contract changes while old and new images can overlap;
- make changesets idempotently tracked by Liquibase and never delete a data volume to “rerun” them;
- record backup, timeout, old-image compatibility, and rollback limits for risky changes.

## Delivery state

```text
PR -> develop -> selective verify/build -> immutable ECR image
   -> image-promotion PR -> review/merge -> Argo CD -> EKS
```

The workflow changes Git desired state; it does not run `kubectl` or auto-merge. Cloud source and
manifests exist, but the EKS environment is currently absent. Therefore Feature 049 source/local
tasks are complete while its live migration/reconcile/E2E/rollback tasks remain pending.

## Verification discipline

“Implemented” above means source exists. “Verified” is valid only for the command, commit,
environment, and date recorded in a feature's `validation.md`. Do not present historical EKS or load
test numbers as current live health.

Core commands:

```powershell
.\mvnw.cmd clean verify
pwsh -NoLogo -NoProfile -File .\infra\scripts\docs\verify-api-documentation.ps1
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

For navigation, return to the [root README](../README.md) or [documentation map](README.md).
