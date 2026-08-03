# Implementation Plan: Campaign Management MVP

**Branch**: `017-campaign-management-mvp` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)  
**Status**: Approved — Avro/Schema Registry implementation authorized (2026-08-03)
**Input**: Approved Feature 017 specification and approved ADRs 0012, 0013, 0015, and 0016

## Summary

Implement Campaign Service as the PostgreSQL-backed control plane that prepares one-Variant flash
sale Campaigns, validates Product truth, allocates Inventory quota idempotently, freezes the
sellable snapshot, progresses `DRAFT -> SCHEDULED -> ACTIVE -> ENDED`, and reliably publishes
Avro `CampaignScheduled.v1` and `CampaignActivated.v1` records through a transactional outbox and
Confluent Schema Registry.

The feature also adds the minimum cross-service compatibility required to run that workflow:

- Gateway route and `SCOPE_CAMPAIGN_ADMIN` enforcement;
- Authentication Service Client Credentials token issuance and two durable machine clients;
- Product internal campaign-validation endpoint with `SCOPE_catalog.read`;
- Inventory allocation authorization narrowed to `SCOPE_inventory.campaign.allocate` plus stable
  machine-readable allocation errors;
- Campaign internal snapshot protected by the separate `flashsale-service` identity and
  `SCOPE_campaign.snapshot.read`.

Implementation uses package-by-feature with pragmatic Clean/Hexagonal boundaries, short local
transactions around remote HTTP calls, optimistic/conditional PostgreSQL concurrency controls, and
OpenFeign only inside Campaign outbound adapters. Kafka/Avro publication is added only after the
durable outbox is working. Redis, purchase reservation, cancellation/release,
and full OIDC are not introduced.

## Technical Context

**Language/Version**: Java 21  
**Framework**: Spring Boot 3.5.16, Spring Security 6.5.x managed by Boot, Spring Cloud Gateway
2025.0.3 in the existing Gateway  
**Primary Dependencies**: Spring MVC, Validation, Data JPA, Security Resource Server, OAuth2 Client,
Spring Authorization Server, Spring Kafka, Spring Cloud OpenFeign, Liquibase, MapStruct 1.6.3,
Actuator, Micrometer Prometheus registry; the approved contract module adds Avro and Confluent
serializer/Schema Registry tooling dependencies
**Storage**: Campaign-owned PostgreSQL (`campaign_db`); Authentication-owned PostgreSQL client
registry (`auth_db`); no Campaign Redis  
**Messaging**: Kafka topic `campaign.lifecycle.v1`, three local partitions, Campaign ID key,
at-least-once outbox delivery, Avro SpecificRecord values, `TopicRecordNameStrategy`,
`BACKWARD_TRANSITIVE` compatibility, controlled Schema Registry registration
**HTTP**: Gateway-to-Campaign admin HTTP; Campaign-to-Product/Inventory internal HTTP using
OpenFeign adapters; Client Credentials token endpoint at Authentication
**Threading**: Servlet/MVC with Java virtual threads enabled declaratively for Campaign; no Reactor
business pipeline  
**Testing**: JUnit 5, AssertJ, Mockito, Spring Security Test, MockMvc/WebTestClient as appropriate,
Testcontainers PostgreSQL/Kafka, migration/contract/concurrency/smoke tests  
**Target Platform**: OCI containers; shared local Compose under `infra/docker`; future Kubernetes
Service/DNS compatible configuration  
**Project Type**: Maven multi-module backend microservices monorepo  
**Performance Goals**: process up to 100 due lifecycle rows per two-second scan and 100 due outbox
rows per 500-ms scan while preserving per-Campaign correctness; no purchase-path latency target is
claimed for this control-plane feature  
**Constraints**: one item/Variant per Campaign; complete allocation only; indefinite schedule
idempotency retention/no key reuse; no transaction across remote HTTP; five-minute-or-shorter
service tokens; no admin-token relay; no distributed lock; no cross-service database access  
**Scale/Scope**: personal-project MVP, one local Kafka broker, multi-instance correctness for
Campaign schedule/lifecycle/outbox races, two lifecycle event types

## Constitution Check

### Pre-research gate

| Gate | Result | Evidence |
|---|---|---|
| Specification traceability | AMENDMENT REQUIRED | Existing Feature 017 is approved, but Avro/Schema Registry changes are a pending amendment |
| Service ownership | PASS | Campaign/Auth own their migrations; Product/Inventory remain authoritative; no shared JPA/domain model |
| Communication | PASS | Gateway ingress; documented HTTP contracts; versioned Kafka contract; Kubernetes DNS-compatible URLs |
| Data and messaging | PASS | PostgreSQL truth; transactional outbox; stable Inventory request ID; idempotent consumers required |
| Root infrastructure ownership | PASS | Compose/topic orchestration planned under `infra/docker`; service runtime/migrations remain in modules |
| Observability | PASS | Actuator/Prometheus already declarative; `X-Trace-Id` propagation and metrics are planned |
| Contracts/dependencies | AMENDMENT REQUIRED | Avro schemas, contract-module dependency, subjects, and registration workflow must be approved |
| Validation | PASS | unit/integration/contract/concurrency/Kafka/smoke/module/reactor checks are planned |
| Architecture decision | AMENDMENT REQUIRED | ADR 0012, ADR 0013, and ADR 0015 are accepted; ADR 0016 is Proposed |

Redis Lua is not applicable because Feature 017 does not implement the purchase hot path or runtime
stock deduction.

### Post-design gate

AMENDMENT PENDING. The proposed Avro contract module preserves service ownership and keeps wire
types at Kafka adapters, but the root module, Schema Registry subjects, compatibility tests, and
ADR 0016 must be approved before code. No platform asset moves into a service and no Constitution
exception is requested.

## Project Structure

### Documentation (Feature 017)

```text
specs/017-campaign-management-mvp/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   └── requirements.md
└── contracts/
    ├── admin-campaign-http.md
    ├── internal-campaign-snapshot-http.md
    ├── product-campaign-validation-http.md
    ├── inventory-allocation-compatibility.md
    ├── service-authentication.md
    └── campaign-lifecycle-events.md
```

`tasks.md` contains the previously generated Feature 017 ledger plus the pending Avro amendment
phase. The amendment tasks must remain unchecked until this plan, contract, and ADR 0016 are
approved.

### Root protocol contract module

```text
contracts/kafka-avro-contracts/
├── pom.xml
└── src/main/avro/com/philia/flashsale/contract/
    └── campaign/lifecycle/v1/
        └── topics/campaign.lifecycle.v1/
            ├── CampaignScheduledV1.avsc
            └── CampaignActivatedV1.avsc
```

This module contains generated transport contracts only. It must not contain JPA entities, domain
aggregates, application services, or service-specific business rules. Adding it to the root Maven
reactor and consuming it from Campaign requires the accepted ADR 0016 and this amendment approval.

### Campaign Service source layout

Planned packages are created only when their slice adds real classes; existing empty global
scaffolding and obsolete `.gitkeep` files are removed during the foundation task.

```text
services/campaign-service/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/campaign/
    │   │   ├── CampaignServiceApplication.java
    │   │   ├── campaign/
    │   │   │   ├── domain/
    │   │   │   │   ├── model/
    │   │   │   │   ├── policy/
    │   │   │   │   ├── event/
    │   │   │   │   └── exception/
    │   │   │   ├── application/
    │   │   │   │   ├── port/in/
    │   │   │   │   ├── port/out/
    │   │   │   │   ├── command/
    │   │   │   │   ├── query/
    │   │   │   │   ├── result/
    │   │   │   │   └── usecase/
    │   │   │   └── adapter/
    │   │   │       ├── in/web/admin/{request,response,mapper}/
    │   │   │       ├── in/web/internal/{response,mapper}/
    │   │   │       ├── in/scheduling/
    │   │   │       ├── out/persistence/jpa/{entity,repository,mapper}/
    │   │   │       ├── out/client/product/{ProductFeignClient,dto,mapper}/
    │   │   │       └── out/client/inventory/{InventoryFeignClient,dto,mapper}/
    │   │   ├── scheduleoperation/
    │   │   │   ├── domain/{model,exception}/
    │   │   │   ├── application/{port/in,port/out,usecase}/
    │   │   │   └── adapter/out/persistence/jpa/{entity,repository,mapper}/
    │   │   ├── outbox/
    │   │   │   ├── application/{port/in,port/out,usecase,model}/
    │   │   │   ├── adapter/in/scheduling/
    │   │   │   ├── adapter/in/web/admin/
    │   │   │   ├── adapter/out/persistence/jpa/{entity,repository,mapper}/
    │   │   │   └── adapter/out/messaging/kafka/
    │   │   ├── security/{configuration,serviceidentity}/
    │   │   ├── configuration/
    │   │   ├── observability/
    │   │   └── websupport/{context,error,filter}/
    │   └── resources/
    │       ├── application.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/
    └── test/java/com/philia/flashsale/campaign/
        ├── architecture/
        ├── campaign/{domain,application,adapter}/
        ├── scheduleoperation/
        ├── outbox/
        ├── security/
        ├── contract/
        └── integration/
```

### Cross-module source changes

```text
services/authentication-service/src/main/java/com/philia/flashsale/authentication/
├── serviceclient/
│   ├── domain/{model,exception}/
│   ├── application/{port/in,port/out,usecase}/
│   └── adapter/
│       ├── in/oauth/
│       └── out/persistence/jpa/{entity,repository,mapper}/
├── security/token/                 # service-token claim customizer
└── configuration/                  # ordered authorization-server chain/properties

services/product-service/src/main/java/com/philia/flashsale/product/
├── campaignvalidation/
│   ├── domain/policy/
│   ├── application/{port/in,port/out,query,result,usecase}/
│   └── adapter/{in/web,out/persistence}/
└── configuration/                  # internal-audience security chain/decoder

services/inventory-service/src/main/java/com/philia/flashsale/inventory/
├── allocation/                     # typed allocation failures + existing HTTP contract refinement
├── configuration/                  # narrow internal allocation chain/decoder
└── websupport/error/               # stable Inventory error-code mapping

services/api-gateway/
├── src/main/resources/application.yml     # campaign-admin route
└── src/{main,test}/java/.../security/     # SCOPE_CAMPAIGN_ADMIN rule/tests

infra/docker/
├── compose.yml                     # Campaign DB/JWT/OAuth/HTTP/Kafka wiring
├── compose.dev.yml                 # existing direct debug port remains
├── .env.example                    # placeholders only, never real secrets
└── kafka/                          # local topic initialization owned by root infrastructure
```

Flash Sale Service receives no purchase/runtime implementation in Feature 017. The feature verifies
its client registration and Campaign snapshot compatibility using contract/integration tests.

**Structure decision**: Core behavior is grouped by business capability, and each capability uses
only the Clean/Hexagonal layers it needs. Service-wide framework policy is not forced into a fake
business feature. Adapters depend inward; framework types never enter Campaign domain/application
ports.

## Implementation Strategy and Dependency Order

### Slice 0 — Build and configuration baseline

1. Update the affected module POMs with only the dependencies justified in `research.md`, including
   `org.springframework.cloud:spring-cloud-starter-openfeign` for Campaign outbound adapters.
2. Configure Campaign virtual threads, datasource/JPA validation, disabled-by-default normal-replica
   Liquibase, JWT trust, OAuth2 clients, Product/Inventory URLs, Kafka producer, scheduler/outbox
   properties, health, and Prometheus exposure.
3. Replace empty global Campaign scaffold packages with the planned package-by-feature root only as
   real classes appear.
4. Keep every affected service context/build green before adding behavior.

### Slice 1 — Campaign domain and database

1. Implement Campaign/Item value rules and lifecycle policies without Spring/JPA.
2. Add Campaign-owned Liquibase migration for Campaign, item, schedule-operation, and outbox tables.
3. Add JPA entities/repositories/mappers and persistence adapters.
4. Verify migrations/constraints/mapping with PostgreSQL Testcontainers.

Kafka is not added to business flow in this slice.

### Slice 2 — Local Campaign administration and edge security

1. Implement create, replace-metadata, replace-item, and admin-detail use cases/controllers.
2. Add Campaign error/validation/ETag/trace contract handling.
3. Add `SCOPE_CAMPAIGN_ADMIN` to the approved Auth administrator mapping.
4. Add Gateway Campaign route and authority rule; confirm no `/internal/**` route exists.
5. Add Campaign public-audience JWT validation and Gateway/Campaign security tests.

Product, Inventory, and Kafka are not called by local draft operations.

### Slice 3 — Machine identity and downstream compatibility

1. Authentication migration creates durable OAuth client/scope tables.
2. Authentication adds fixed client provisioning, durable RegisteredClient adapter, ordered OAuth2
   token endpoint chain, RS256 service-token customization, and standard protocol tests.
3. Product adds the internal campaign-validation feature and an internal-audience/subject/scope
   security chain.
4. Inventory narrows allocation authorization, introduces stable allocation error distinctions, and
   preserves the existing endpoint/envelope.
5. Campaign adds the background-capable Client Credentials manager and scope-specific OpenFeign
   adapters. Feign interfaces remain transport-only; adapters translate wire DTOs and failures into
   application ports/results.
   Product uses a 500-ms connect/1,200-ms read timeout; Inventory uses a 500-ms
   connect/1,000-ms read timeout. Automatic Feign retry is disabled so schedule recovery remains the
   only retry owner and always reuses the durable Inventory request identity.
6. Cross-module contract tests prove token/audience/subject/scope isolation and no admin-token relay.

### Slice 4 — Idempotent schedule and durable event creation

1. Create/load the schedule operation in a short transaction, locking draft mutation while active.
2. Validate Product outside a transaction.
3. Check Campaign price/currency snapshot rules.
4. Allocate Inventory outside a transaction using the stable request ID.
5. In a final short transaction, revalidate Campaign version/configuration, freeze snapshot,
   transition to `SCHEDULED`, complete the operation, and insert exactly one
   `CampaignScheduled.v1` outbox row.
6. Add replay/conflict/concurrency/ambiguous-timeout/crash-after-allocation recovery tests.

The schedule endpoint is not considered complete until outbox creation is atomic with the state
transition, even though broker publication is implemented in the next slice.

### Slice 5 — Outbox Kafka publication and recovery

1. Add the approved Avro contract module, generated SpecificRecord types, and compatibility fixtures.
2. Add Schema Registry client configuration with `auto.register.schemas=false` for stable profiles
   and `TopicRecordNameStrategy` for lifecycle subjects.
3. Add Spring Kafka producer configuration only now, using the generated Avro values.
4. Add multi-instance-safe due-row claim/lease and earliest-aggregate-version ordering.
5. Publish the approved Avro record with Campaign ID key and producer idempotence/`acks=all`.
6. Implement success, exponential backoff, tenth-attempt terminal failure, lease reclaim, and
   authenticated requeue of the same event.
7. Add real Kafka-compatible and Schema Registry compatibility integration tests for key/record/
   order/retry/duplicate identity and Registry/Kafka outage recovery.

### Slice 6 — Lifecycle, activation event, ending, and snapshot

1. Add two-second/batch-100 lifecycle scanning.
2. Activate with conditional status/version/time update and atomically insert one
   `CampaignActivated.v1` outbox row.
3. Implement manual recovery activation with identical domain conditions.
4. End ACTIVE Campaigns at `endAt` without an ended event.
5. Add internal snapshot query protected by internal audience, `flashsale-service` subject, and
   `SCOPE_campaign.snapshot.read`.
6. Verify multi-instance races, event order, immutability, and snapshot token substitution denial.

### Slice 7 — Local topology and operational evidence

1. Update root Compose environment/service dependencies and local Kafka topic provisioning.
2. Register the approved lifecycle subjects through controlled tooling; never register from
   application startup.
3. Apply Auth and Campaign migrations with one-off non-web migration processes.
4. Run the end-to-end smoke path through Gateway/Auth/Campaign/Product/Inventory/PostgreSQL/Kafka/
   Schema Registry.
5. Verify health/readiness/Prometheus, W3C Kafka header trace propagation, redaction, service-token
   renewal, outbox failure/requeue, Registry outage, and restart recovery.
6. Run affected-module builds, the full reactor, and applicable configuration validation.

## Transaction Boundaries

```text
TX A: create/load schedule operation + stable inventoryRequestId + fingerprint
COMMIT

HTTP: obtain/reuse service token -> Product validation
HTTP: obtain/reuse scope-specific token -> Inventory allocate (stable requestId)

TX B: re-read/revalidate Campaign + freeze snapshot + DRAFT->SCHEDULED
      + operation COMPLETED + CampaignScheduled.v1 outbox insert
COMMIT
```

No PostgreSQL transaction remains open during Product, Inventory, token, or Kafka network calls.
Inventory ambiguity is resolved by recalling the same idempotent request. Outbox broker ambiguity is
resolved by the same event ID plus consumer idempotency.

## Security and Secret Plan

- Administrator JWT: `aud=flash-sale-api`, `SCOPE_CAMPAIGN_ADMIN`, validated at Gateway and
  Campaign; never persisted/relayed downstream.
- Campaign JWT: `sub=campaign-service`, `aud=flash-sale-internal-api`, five-minute maximum,
  scope-specific Product/Inventory tokens cached only in Campaign process memory.
- Flash Sale JWT: `sub=flashsale-service`, same internal audience,
  `SCOPE_campaign.snapshot.read`; independent client secret/token.
- Client secrets enter containers through real untracked environment/secret injection, are never in
  Git/images/logs/databases as plaintext, and Authentication persists only encoded values.
- Authentication/JWT/OAuth failures redact credentials and distinguish protocol errors from Campaign
  business errors.
- Ordered security chains prevent public/admin and internal audiences from substituting for one
  another.

## Failure and Recovery Plan

| Failure window | Recovery |
|---|---|
| Product unavailable before allocation | Campaign stays DRAFT; same schedule operation/key resumes |
| Product/Inventory business rejection | Operation FAILED; Campaign stays DRAFT; no event; same key/hash/config may reopen it with the stable request ID |
| Inventory timeout/unknown outcome | Operation remains resumable; recall same `inventoryRequestId` |
| Crash after allocation response | retry/recovery recalls same request and finalizes once |
| Concurrent schedule | unique operation/index/version gates; one allocation/result/event |
| Concurrent activation/end | conditional update permits one transition |
| Kafka or Schema Registry unavailable | durable outbox remains pending and retries; Campaign state remains truth |
| Publisher crashes after send | same event may redeliver; identity unchanged; consumer deduplicates |
| Ten publish failures | row FAILED; later events for Campaign blocked until authorized requeue |
| Auth token endpoint unavailable | unexpired cached token may continue; otherwise 503/resumable operation |
| Wrong audience/subject/scope | 401/403 at owning service; no mutation |

## Observability Plan

- Continue declarative `health`, `info`, and `prometheus` exposure; do not instantiate a Prometheus
  registry in Java.
- Propagate/normalize `X-Trace-Id` across admin/internal HTTP, OAuth acquisition, Product/Inventory,
  schedule operations, scheduler-created work, outbox metadata, and structured logs. For Kafka,
  inject W3C `traceparent`/`tracestate` headers; do not require a trace ID in the Avro business body.
- Record semantic counters/timers/gauges for Campaign commands, schedule outcomes/latency, downstream
  calls, lifecycle transitions, outbox pending/failed/oldest age, publisher retries, lease recovery,
  and operator requeues.
- Never tag metrics with Campaign ID, user ID, idempotency key, token, or unbounded error text.
- Preserve a migration path to Micrometer Tracing/OpenTelemetry; OTLP export is not partially added
  in this feature.

## Validation Matrix

| Changed area | Minimum evidence |
|---|---|
| Campaign domain/application | focused JUnit unit tests |
| Campaign/Auth schemas | Liquibase + PostgreSQL Testcontainers migration/rollback/constraint tests |
| Admin/internal HTTP | controller contract tests for methods, paths, headers, body, statuses |
| Gateway | route forwarding and 401/403/authority/header tests |
| OAuth issuance | valid/invalid client, status/grant/scope, claims/TTL/no refresh token tests |
| Product/Inventory/Campaign security | audience/subject/scope substitution matrix |
| Schedule | PostgreSQL concurrency + stable request/replay/crash recovery |
| Outbox/Kafka/Schema Registry | Avro serialization, subject compatibility, real broker-compatible key/order/retry/reclaim/requeue, and Registry outage tests |
| Lifecycle | two-worker activation/end race tests with controllable Clock |
| Observability | health/Prometheus/trace/redaction tests |
| Local environment | Compose config plus full smoke path |

Required build commands:

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/inventory-service,services/campaign-service -am verify
.\mvnw.cmd clean verify
```

Required local configuration validation:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
```

No Kubernetes asset is changed in the MVP plan. If a later task adds an approved overlay, it must
also run `kubectl apply --dry-run=client -k <overlay>`.

## Planning Artifacts and ADRs

- [Research](./research.md)
- [Data model](./data-model.md)
- [Quickstart](./quickstart.md)
- [Service authentication contract](./contracts/service-authentication.md)
- [Admin Campaign contract](./contracts/admin-campaign-http.md)
- [Internal snapshot contract](./contracts/internal-campaign-snapshot-http.md)
- [Product validation contract](./contracts/product-campaign-validation-http.md)
- [Inventory compatibility contract](./contracts/inventory-allocation-compatibility.md)
- [Kafka lifecycle contract](./contracts/campaign-lifecycle-events.md)
- [ADR 0016: Avro and Schema Registry](../../docs/adr/0016-avro-schema-registry-campaign-lifecycle.md)
- `docs/adr/0012-campaign-oauth2-client-credentials.md`
- `docs/adr/0013-flashsale-campaign-snapshot-identity.md`
- `docs/adr/0015-campaign-openfeign-http-clients.md`

## Complexity Tracking

No Constitution violation or exception is requested. The Avro contract module and Schema Registry
client are an approved-amendment dependency change, not a shared business-domain library.

The cross-module work is required by the approved end-to-end security/ownership contracts, but each
module remains independently deployable and the rollout order avoids a lockstep deployment:

```text
Authentication capability
  -> Product/Inventory acceptance
  -> Campaign usage
  -> Gateway admin exposure
```

## Approval History and Next Gate

- 2026-07-30 — Project owner approved this plan and its research, data-model, quickstart, HTTP,
  security, compatibility, and Kafka contract artifacts.
- 2026-08-02 — The transport decision was amended from `RestClient` to OpenFeign for the two
  Campaign outbound HTTP adapters; plan re-approval is required before production implementation.
- 2026-08-03 — Project owner explicitly approved implementing T053–T055 with the repository
  OpenFeign skill; ADR 0015 and this plan amendment are accepted.
- 2026-08-03 — Project owner approved the Avro/Schema Registry amendment. ADR 0016, the
  protocol-only contract module, generated SpecificRecords, and compatibility gates are accepted;
  live publisher/Registry runtime work remains in later tasks.

The plan and task ledger are approved for implementation. Runtime work must still follow the
dependency-ordered tasks and record validation evidence in `quickstart.md`.
