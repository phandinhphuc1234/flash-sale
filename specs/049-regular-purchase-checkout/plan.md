# Implementation Plan: Regular Purchase Checkout

**Branch**: `codex/regular-purchase-checkout` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Status**: Approved for implementation by project owner on 2026-09-03

**Input**: Approved A/A/A/A/A decisions in `specs/049-regular-purchase-checkout/spec.md`

## Summary

Add authenticated Buy Now and all-or-nothing Cart checkout while preserving Cart as mutable saved
intent and keeping Order as the purchase-workflow owner. Order validates an immutable request against
Cart- and Product-owned HTTP contracts, asks Inventory to create one atomic five-minute regular-stock
hold, then commits a multi-line Order, Purchase Saga, Payment command, and outbox facts. Existing
Payment/Stripe contracts remain unchanged. Payment success or terminal failure drives Inventory hold
confirmation/release through versioned Kafka commands and facts; a confirmed Cart purchase emits an
idempotent conditional Cart-reconciliation command that never removes later shopper edits.

Delivery is schema-first and local-first. Database changes use forward-only expand migrations,
runtime entry points remain disabled until every affected image and migration is ready, and the
existing Flash Sale purchase path is kept unchanged and covered by regression gates.

## Technical Context

**Language/Version**: Java 21; PowerShell 7 for local and cloud validation runners; Avro v1/v2
SpecificRecord contracts

**Framework**: Spring Boot 3.5.16, Spring Data JPA, Spring Security OAuth2, Spring Cloud OpenFeign,
Spring Kafka, Liquibase, Actuator/Micrometer

**Primary Dependencies**: Existing `common-web`, `kafka-avro-contracts`, Spring Kafka, Confluent
Avro serializer, PostgreSQL driver, OpenFeign/OAuth2 client support, runtime Prometheus registry,
JUnit 5, ArchUnit, and Testcontainers. Inventory and Cart gain the existing monorepo Kafka/Avro
dependencies; Order gains the existing OpenFeign and OAuth2-client dependencies. No new library or
framework version is introduced.

**Storage**:

- `cart_db`: Cart/item revisions and Cart reconciliation inbox; Cart never stores price or stock
  truth.
- `inventory_db`: regular stock holds/items, command inbox, outbox facts, movements, and expiry
  state; PostgreSQL remains the regular-stock truth.
- `order_db`: regular-purchase intake/idempotency state, generalized multi-line Order, generalized
  Purchase Saga participant reference, inboxes, and existing Order outbox.
- Product and Payment schemas remain unchanged unless implementation uncovers an approved additive
  operational need.

**Testing**: JUnit 5, Spring Boot Test, ArchUnit, Testcontainers PostgreSQL/Kafka, Avro schema tests,
HTTP contract tests, Maven module/full-reactor verify, Docker Compose E2E, PowerShell replay and
concurrency runner, Kustomize dry-run, migration rehearsal, and existing Stripe test-mode smoke

**Target Platform**: Local Docker Compose first; Linux containers on the existing AWS EKS cloud
environment after all local gates pass

**Project Type**: Maven monorepo of independently deployable Spring Boot microservices with shared
Kafka contract and infrastructure modules

**Performance Goals**: Return accepted purchase or actionable rejection within five seconds under
the representative test load; one hundred equivalent retries remain one semantic purchase; stock
concurrency tests never oversell.

**Constraints**:

- Cart checkout is all-or-nothing and one accepted submission creates one Order/Payment.
- Product owns sellability and price; Inventory owns regular stock and holds.
- Regular hold TTL is five minutes; Payment deadline is hold expiry minus 30 seconds.
- Client idempotency is scoped by `(shopperId, idempotencyKey)` and canonical request fingerprint.
- No downstream HTTP call is made while an Order database transaction is open.
- Kafka delivery is at-least-once; every command/fact consumer is idempotent and conflict-aware.
- Payment success remains dominant; automatic refund behavior is not introduced.
- Flash Sale Redis/Lua admission, campaign pricing, reservation, and quota behavior is unchanged.
- No destructive database rollback; rollback disables new regular intake first and preserves rows.

**Scale/Scope**: Four affected business services (Cart, Product, Inventory, Order), Authentication
configuration for one machine client, API Gateway route reuse, existing Payment/Stripe runtime, two
public commands, three internal HTTP capabilities, three Kafka topic families, consumer-specific
DLTs, additive migrations in three owning databases, and one aggregate local/cloud release.

No `NEEDS CLARIFICATION` remains.

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

| Gate | Result | Design evidence |
|---|---|---|
| Specification traceability | PASS | FR-001–FR-022 and SC-001–SC-007 map to the design artifacts and implementation groups below. |
| Service ownership | PASS | Cart owns snapshots/reconciliation, Product owns quotes, Inventory owns holds, Order owns workflow/Order state, and Payment owns provider state; no cross-service DB access or FK is added. |
| Clean/Hexagonal architecture | PASS | Each capability uses feature-oriented domain/application ports with web, Feign, Kafka, Avro, JPA, and scheduling kept in adapters. |
| Communication | PASS | Public calls enter through Gateway, synchronous decisions use documented internal HTTP, and durable post-acceptance effects use versioned Kafka contracts. |
| Data and messaging | PASS | PostgreSQL is durable truth; hold creation is one local Inventory transaction; Order/Inventory/Cart use transactional outbox/inbox and stable identities. Flash Sale Redis remains untouched. |
| Root infrastructure | PASS | Topic/schema provisioning, Compose, Kubernetes, monitoring, and runners remain under root `infra/`; migrations/config stay in service modules. |
| Observability | PASS | Existing Actuator/Prometheus endpoints remain declarative; regular intake, hold, Saga, outbox, reconciliation, expiry, and conflict signals are planned without constructing registries in business code. |
| Contracts and dependencies | PASS | Public/internal HTTP, Kafka records, keys, compatibility, auth scopes, DLTs, and dependency additions are identified before implementation. |
| Validation | PASS | Unit, architecture, migration, HTTP/Avro contract, idempotency, concurrency, recovery, E2E, regression, Maven, Kubernetes, rollout, and rollback checks are specified. |

### ADR gate

No new ADR is required. Accepted ADR 0018 already makes Order the Purchase Saga owner, separates
Payment commands from facts, requires outbox/inbox delivery, and defines success-dominant recovery.
Feature 049 adds a new Inventory-owned participant and Cart cleanup contract without moving data or
workflow ownership. A separate ADR is required only if implementation proposes a new coordinator,
Cart-owned checkout orchestration, synchronous charging, cross-service persistence, automatic
refund, or a change to the Flash Sale hot path.

## Project Structure

### Documentation

```text
specs/049-regular-purchase-checkout/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── public-checkout-http.md
│   ├── internal-checkout-http.md
│   ├── regular-stock-hold-kafka.md
│   ├── order-regular-lifecycle-kafka.md
│   └── cart-reconciliation-kafka.md
├── checklists/requirements.md
└── tasks.md                              # generated only after plan approval
```

### Shared contracts and infrastructure

```text
contracts/kafka-avro-contracts/src/main/avro/topics/
├── flashsale.inventory.regular-hold.commands.v1/
├── flashsale.inventory.regular-hold.events.v1/
├── flashsale.cart.checkout.commands.v1/
└── flashsale.order.events.v1/            # additive regular-purchase v2 facts

infra/
├── docker/                               # topic init, Compose config, local E2E
├── k8s/overlays/cloud/config/            # service URLs, scopes, topics, feature flags
├── monitoring/                           # regular checkout/hold/Saga metrics and alerts
└── scripts/gitops/                       # schema, migration, release, E2E, rollback gates
```

### Order Service

```text
services/order-service/src/main/java/com/philia/flashsale/order/
├── regularpurchase/
│   ├── domain/{model,policy,exception}/
│   ├── application/{command,result,port,usecase}/
│   └── adapter/
│       ├── in/web/
│       └── out/client/{cart,product,inventory}/
├── order/                                # generalize source and multiple immutable lines
├── purchasesaga/                         # participant type/reference and regular outcomes
├── outbox/                               # new command/fact dispatchers
├── security/
├── configuration/
└── observability/

services/order-service/src/main/resources/
├── application.yml
└── db/changelog/changes/003-add-regular-purchase-checkout.sql
```

### Inventory Service

```text
services/inventory-service/src/main/java/com/philia/flashsale/inventory/
├── regularhold/
│   ├── domain/{model,policy,exception}/
│   ├── application/{command,result,port,usecase}/
│   └── adapter/
│       ├── in/{web,messaging/kafka,scheduling}/
│       └── out/{persistence/jpa,messaging/kafka}/
├── stock/                                # locking/availability integration only
├── movement/
├── outbox/
├── configuration/
└── observability/

services/inventory-service/src/main/resources/
├── application.yml
└── db/changelog/changes/002-add-regular-stock-holds.sql
```

### Cart Service

```text
services/cart-service/src/main/java/com/philia/flashsale/cart/
├── checkout/
│   ├── domain/{model,policy,exception}/
│   ├── application/{command,query,result,port,usecase}/
│   └── adapter/
│       ├── in/{web,messaging/kafka}/
│       └── out/persistence/jpa/
├── domain/                               # item/cart revision support
├── adapter/in/web/                       # additive revision fields in Cart reads
├── configuration/
└── observability/

services/cart-service/src/main/resources/
├── application.yml
└── db/changelog/changes/002-add-checkout-revisions-and-inbox.yaml
```

### Product and Authentication Services

```text
services/product-service/src/main/java/com/philia/flashsale/product/
└── catalogquery/                         # Order-only purchase quote input adapter/use case

services/authentication-service/src/main/resources/application.yml
                                          # provision order-service client/scopes
```

**Structure Decision**: Regular purchase intake is an Order-owned business feature. Inventory hold
and Cart checkout reconciliation are service-owned features. Existing generic Order Saga/outbox
capabilities are extended through their ports rather than copied. Generated Avro, Feign wire DTOs,
JPA entities, and Spring annotations do not enter domain models.

## Dependency and Boundary Rules

```text
Gateway -> Order public web adapter
        -> RegularPurchase use case
        -> CartSnapshotPort / ProductQuotePort / RegularStockHoldPort
        <- OAuth2 OpenFeign adapters
        -> atomic regular-intake persistence port
        <- Order PostgreSQL adapter

Order outbox -> PaymentRequestedV1 (unchanged)
             -> regular-hold confirm/release commands

Inventory Kafka adapter -> RegularHold use case
                        -> atomic hold + inbox + movement + outbox port
                        <- Inventory PostgreSQL adapter

Inventory outcome -> Order Saga -> terminal Order fact
                                -> Cart reconciliation command when source=CART

Cart Kafka adapter -> conditional reconciliation use case
                   -> Cart row/item lock + inbox transaction
```

- Public controllers derive `shopperId` from JWT; no owner ID is accepted from the body.
- Internal controllers accept only the exact `order-service` subject and required least-privilege
  scope. Cart reconciliation accepts only versioned Kafka commands from `order-service`.
- Product quote, Cart snapshot, and Inventory hold wire DTOs are mapped at adapter boundaries.
- Order does not keep an open database transaction across Cart/Product/Inventory HTTP calls.
- One atomic Order persistence port commits accepted intake, Order/lines, Saga, and all initial
  outbox rows. One atomic Inventory port owns multi-item hold all-or-nothing behavior.

## Contract Impact

| Surface | Planned change |
|---|---|
| Public Order HTTP | Add `POST /api/v1/orders/buy-now` and `POST /api/v1/orders/cart-checkouts`; both require JWT and `Idempotency-Key`. Extend Order reads additively with purchase source and generic stock-hold metadata. |
| Cart HTTP | Existing public Cart response adds `cartVersion` and `itemVersion`; add an Order-only internal checkout-snapshot endpoint. CRUD behavior remains unchanged. |
| Product HTTP | Add an Order-only batch purchase-quote endpoint returning current sellability, price, currency, identity, and version. Existing Cart display endpoint remains unchanged. |
| Inventory HTTP | Add an Order-only atomic regular-hold creation endpoint; it is idempotent by purchase request and fingerprint. `requestedAt` is an audit value accepted only within the Inventory-owned, configurable 90-second clock-skew bound. |
| Payment Kafka/HTTP | Reuse `PaymentRequestedV1`, Payment result events, Checkout Session, webhook, and query APIs unchanged. |
| Regular hold Kafka | Add confirm/release commands and confirmed/released/expired facts, keyed by `orderId`; add Inventory-command and Order-result DLTs. |
| Cart reconciliation Kafka | Add one conditional cleanup command keyed by `cartId`; add Cart consumer DLT. |
| Order facts | Preserve all existing Flash Sale V1 records unchanged. Add regular-purchase V2 records because V1 requires `campaignId` and Flash Sale `reservationId`. |
| Service authentication | Provision `order-service` with `cart.checkout-snapshot.read`, `catalog.purchase-quote.read`, and `inventory.regular-hold.write`. |

## Phase 0: Research Outputs

[research.md](./research.md) records these resolved decisions:

1. Order remains the orchestrator; Cart never becomes a checkout coordinator.
2. Synchronous HTTP is used only for pre-acceptance validation and atomic hold acquisition.
3. Payment contracts stay unchanged; regular and Flash Sale purchases converge after Order commit.
4. Inventory reserves all lines in one transaction with deterministic row-lock ordering.
5. Durable Order intake state closes idempotency and crash-recovery gaps without long DB transactions.
6. Cart cleanup is asynchronous, conditional by item revision, and at-most one semantic effect.
7. Existing Flash Sale V1 Order facts remain untouched; regular purchases use additive V2 facts.
8. Expand-first migrations and feature flags define a non-destructive rollout/rollback boundary.

## Phase 1: Design Outputs

- [Data model](./data-model.md)
- [Public checkout HTTP](./contracts/public-checkout-http.md)
- [Internal checkout HTTP](./contracts/internal-checkout-http.md)
- [Regular stock-hold Kafka](./contracts/regular-stock-hold-kafka.md)
- [Regular Order lifecycle Kafka](./contracts/order-regular-lifecycle-kafka.md)
- [Cart reconciliation Kafka](./contracts/cart-reconciliation-kafka.md)
- [Quickstart](./quickstart.md)

Post-design Constitution re-check: PASS; no justified violation remains.

## Implementation Groups

The generated `tasks.md` preserves this dependency order. The project owner approved its
implementation on 2026-09-03.

### G1 — Contract-first foundation

- Add documented public/internal HTTP contracts, OpenAPI examples, error codes, Avro schemas, keying,
  Schema Registry compatibility tests, topic/DLT inventory, and frontend handoff updates.
- Add feature flags and configuration contracts in disabled state.
- Gate: contract module verify, schema compatibility, static API documentation validation.

### G2 — Cart snapshot and safe reconciliation

- Add Cart/item monotonic revisions through an expand migration and expose them additively.
- Add exact-subject/scoped internal snapshot endpoint.
- Add reconciliation consumer, durable inbox, conditional item removal, conflict-safe replay, DLT,
  metrics, and tests for edits made after checkout.
- Gate: Cart module verify, migration tests, HTTP security/contract tests, replay/concurrency tests.

### G3 — Product purchase quotes and service authentication

- Add Order-only batch quote endpoint and least-privilege security chain.
- Provision durable `order-service` OAuth client and secret/config boundaries in local/cloud assets.
- Gate: Product/Auth module tests, wrong subject/scope rejection, no expansion of existing Cart or
  Campaign client privileges.

### G4 — Inventory atomic regular holds

- Add hold aggregate/items, create API, deterministic Inventory row locks, five-minute expiry,
  movements, command inbox, fact outbox, relay, expiry scheduler, and recovery metrics.
- Add Kafka/Avro dependencies already governed by the root monorepo.
- Gate: Inventory migration/domain/HTTP/Kafka tests plus oversell, all-or-nothing, expiry,
  replay/conflict, outbox, and concurrency evidence.

### G5 — Order regular-purchase intake

- Add Buy Now and Cart checkout adapters, canonical fingerprint policy, durable intake checkpoints,
  OpenFeign/OAuth2 adapters, multi-line Order/source model, and additive Order migration.
- Persist Order, lines, generalized Saga, `OrderCreatedV2`, and existing `PaymentRequestedV1` in one
  final transaction after an Inventory hold succeeds.
- Gate: Order HTTP/security/idempotency, price mismatch, dependency failure/recovery, migration,
  outbox, and multi-line total tests.

### G6 — Generalized Order Saga

- Route Payment outcomes by participant type without changing the Flash Sale branch.
- Publish regular hold confirm/release commands; consume monotonic Inventory outcomes; finalize
  Order or enter the existing success-dominant/manual-review policy.
- Emit Cart reconciliation only after confirmed regular Cart purchase.
- Gate: paid, unpaid, expired, late-success, duplicate, reordering, DLT, and Flash Sale regression
  scenarios.

### G7 — Aggregate local E2E and operational readiness

- Add one scenario-selectable local runner for Buy Now, Cart paid, Cart later-edit preservation,
  insufficient stock, price conflict, unpaid release, expiry, replay, and concurrency.
- Add sanitized metrics, alerts, dashboards/runbook, readiness dependencies, and trace continuity.
- Gate: affected modules, aggregate local runner, full Maven reactor, Docker/Kafka/Registry/Postgres,
  architecture tests, `git diff --check`, and Kustomize dry-run.

### G8 — One reviewed cloud rollout and rollback rehearsal

- Provision topics/subjects idempotently while production flags are disabled.
- Build affected immutable images once, run sequential service-owned migration Jobs before image
  promotion, merge one GitOps tag PR, and wait for Argo/rollouts.
- Enable regular purchase intake through a reviewed config change only after every service is ready.
- Run Gateway/Stripe Buy Now and Cart E2E plus replay/concurrency-safe cloud smoke.
- Rehearse rollback by disabling intake first, preserving data/topics, and restoring compatible
  immutable tags; never reverse/drop migrations.

## Runtime Design

### Pre-acceptance and durable intake

```text
Browser -> Gateway -> Order POST Buy Now / Cart checkout
  -> persist/lock idempotency intake (shopper + key + fingerprint)
  -> Cart snapshot (Cart only) -> Product authoritative quote
  -> reject on Cart/price/sellability conflict with no lasting hold or Order
  -> Inventory atomic multi-item hold (5 minutes)
  -> one order_db transaction:
       intake ACCEPTED + Order/lines + PurchaseSaga
       + OrderCreatedV2 outbox + PaymentRequestedV1 outbox
  -> HTTP 201 (or exact replay result)
```

The intake row stores phase/checkpoint and stable generated identities. Retry resumes the same
request and repeats only idempotent downstream operations. Inventory hold creation is idempotent by
`purchaseRequestId` plus fingerprint. An unexpected crash after Inventory commit but before Order
commit cannot oversell or duplicate: retry finds the same hold, while the five-minute Inventory
expiry is the final orphan safety net.

For `CART`, the initial durable `RECEIVED` intake has no `cartId`, because the browser may never
choose one. The Cart internal snapshot returns that owner-bound identity; the Order persistence
transition stores `cartId` and `cartVersion` together with `SNAPSHOT_VALIDATED`. The forward-only
Order migration enforces this state-aware boundary, including the valid case of rejection before a
Cart snapshot exists.

### Paid Cart path

```text
PaymentSucceededV1 -> Order Saga -> ConfirmRegularStockHoldV1
Inventory transaction -> hold CONFIRMED + on-hand deduction + movement + outcome outbox
RegularStockHoldConfirmedV1 -> Order CONFIRMED + Saga COMPLETED + OrderConfirmedV2 outbox
Order outbox -> ReconcilePurchasedCartSnapshotV1 (Cart source only)
Cart transaction -> delete only items with matching itemVersion + variant + quantity; inbox replay-safe
```

### Unpaid and expiry paths

```text
PaymentFailedV1 -> Order Saga -> ReleaseRegularStockHoldV1
Inventory -> hold RELEASED (one effect) -> RegularStockHoldReleasedV1
Order -> CANCELLED/EXPIRED + Saga COMPENSATED
Cart remains unchanged

Inventory scheduler reaches expiresAt first -> hold EXPIRED + fact
Order consumes fact -> Order EXPIRED unless a verified higher-version success requires recovery/review
```

### Late success

Success remains dominant. If a later verified Payment success arrives while release is in flight,
Order attempts confirmation. If Inventory still owns an unexpired/recoverable hold, it confirms and
Order completes. If stock can no longer be safely confirmed after release/expiry, Order uses the
existing durable manual-review boundary and emits no automatic refund. This feature does not invent
financial policy.

## Migration and Rollout Strategy

1. Apply additive Cart, Inventory, and Order migrations with old entry points still active and the
   new regular-purchase flag disabled.
2. Keep legacy columns and V1 records. Backfill existing Order/Saga rows as `FLASH_SALE`; make new
   generic source/participant columns authoritative for upgraded code.
3. Deploy consumers before producers: Cart/Inventory consumers, then Order consumers/intake.
4. Provision topics/subjects before enabling any producer.
5. Enable regular intake only after Argo is Healthy and all affected Deployments use the reviewed
   immutable release.
6. Roll back by disabling intake first and restoring compatible images. Never drop new tables,
   columns, inbox/outbox rows, holds, or schemas. Once real regular Orders exist, an image that
   cannot read `REGULAR` source/participant values is not a safe rollback target; use a compatible
   forward fix instead.

## Validation Strategy

| Layer | Required evidence |
|---|---|
| Domain/unit | Canonical request fingerprint, totals, five-minute/30-second policy, Cart conditional cleanup, hold transitions, Saga routing. |
| Architecture | Domain/application do not depend on web/JPA/Feign/Kafka/Avro; service databases remain isolated. |
| Migration | Fresh schema, upgrade from current schema, backfill, old image startup against expanded schema before intake enablement. |
| HTTP contract/security | JWT owner derivation, required idempotency header, validation/error envelopes, exact internal subject/scope, bounded timeout/no unsafe retry. |
| Kafka/Registry | SpecificRecord schema tests, keys, compatibility, outbox publication, inbox replay/conflict, DLT, ordering and stale-version behavior. |
| Concurrency | Competing holds across overlapping item sets, deterministic lock order, no oversell/deadlock leak, 100 duplicate submissions/outcomes. |
| E2E | Buy Now and multi-item Cart through Gateway, Payment/Stripe, Inventory finalization, Order query, conditional Cart cleanup. |
| Regression | Existing Flash Sale purchase/Saga, Payment, Cart CRUD, Authentication, Product, Inventory campaign allocation, Gateway/Swagger. |
| Operations | Metrics/alerts/readiness, redacted diagnostics, migration Job, topic provisioning, immutable image promotion, Argo reconciliation, rollback rehearsal. |

## Complexity Tracking

No Constitution violation requires an exception. The cross-service workflow is unavoidable because
the approved owners of Cart, Product, Inventory, Order, and Payment remain separate; the design uses
explicit contracts and local transactions rather than weakening those boundaries.
