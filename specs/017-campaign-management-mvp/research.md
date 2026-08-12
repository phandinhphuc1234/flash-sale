# Phase 0 Research: Campaign Management MVP

**Feature**: `017-campaign-management-mvp`  
**Date**: 2026-07-30  
**Status**: Complete

## Repository baseline

- Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3, and MapStruct 1.6.3 are managed by the root
  reactor.
- Campaign Service is a runnable Spring MVC/Actuator/Liquibase scaffold with no datasource, JPA,
  security, Kafka, or business code.
- Gateway has Auth, Product, and Inventory routes but no Campaign route or authority rule.
- Authentication issues first-party RS256 administrator JWTs and exposes JWKS, but has no OAuth2
  token endpoint or durable service-client registry.
- Product has no campaign-validation endpoint.
- Inventory owns the existing campaign-allocation endpoint, request idempotency, and physical-stock
  locking. Its current internal authorization is the broader `SCOPE_INVENTORY_WRITE`.
- Kafka exists only in local infrastructure; no application module currently publishes a promoted
  integration event.
- Repository-wide OpenTelemetry export remains planned. Feature 017's Avro amendment uses W3C
  `traceparent`/`tracestate` Kafka headers; existing HTTP `X-Trace-Id` remains a compatibility
  boundary until the repository-wide tracing feature is approved.

## R1. Service and package architecture

**Decision**: Use package-by-feature with pragmatic Clean/Hexagonal boundaries.

Campaign is a core business feature and receives `domain`, `application`, and `adapter` boundaries.
Schedule-operation is a distinct correctness workflow with its own domain/application/persistence
slice. Outbox is an operational slice with application ports and persistence/Kafka adapters; it does
not receive an empty domain package. Security, configuration, observability, and web error handling
remain service-wide technical packages.

Within a feature the dependency direction is:

```text
adapter -> application -> domain
configuration -> adapter + application
```

Domain code does not depend on Spring, JPA, HTTP, Kafka, or OAuth classes. HTTP/JPA/Kafka DTOs remain
inside their adapters and are mapped to application commands/results or domain types.

**Rejected**:

- global service-wide `adapter/application/domain` roots, because business intent becomes hard to
  locate as features grow;
- one package for every technical class type, because it creates cross-feature coupling;
- creating empty Clean Architecture layers for slices without corresponding behavior.

## R2. Synchronous communication

**Decision**: Use documented HTTP contracts and Spring Cloud OpenFeign for Campaign-to-Product and
Campaign-to-Inventory calls.

Campaign is already a servlet service and these schedule calls are blocking request/response calls.
OpenFeign keeps the boundary synchronous and works with virtual threads without introducing WebFlux
into the service. Feign interfaces and transport DTOs remain inside each feature's outbound adapter;
the application layer depends only on capability-oriented output ports. Calls occur outside Campaign
database transactions and use bounded connect and response timeouts. Transport failures map to the
approved 503 Campaign errors. No application retry may create a new Inventory request ID.

Spring Security's OAuth2 client support will attach the service bearer token to outbound requests.
Two client-side registration IDs share the Campaign client credentials but request different scope
sets, so Product validation does not receive Inventory authority and Inventory allocation does not
receive Product authority. The Feign request configuration must preserve the registration-specific
token selection, `X-Trace-Id` propagation, bounded timeouts, and response/error translation.

The approved timeout budget is explicit: Product uses 500 ms connect / 1,200 ms read; Inventory
uses 500 ms connect / 1,000 ms read. Inventory takes the stricter 1,000-ms value from the supplied
2,500-ms Campaign budget rather than the conflicting 1,500-ms example default. Feign automatic
retry is disabled; durable schedule recovery owns retries and reuses the same Inventory request ID.

**Rejected**:

- gRPC, because the Constitution currently requires documented synchronous HTTP and the MVP already
  defines HTTP contracts;
- WebClient/Reactor, because it adds a second programming model without a reactive end-to-end path;
- Spring `RestClient`, because OpenFeign keeps the same adapter boundary while reducing repetitive
  request construction for the two typed internal clients;
- a resilience/circuit-breaker dependency in Feature 017, because circuit breaking is outside the
  approved MVP and bounded failure mapping is sufficient for the first slice.

## R3. OAuth2 Client Credentials issuance

**Decision**: Extend Authentication Service with Spring Boot's OAuth2 Authorization Server starter,
configured only for registered `client_credentials` clients needed by this feature.

Spring Authorization Server supports Client Credentials, `client_secret_basic`, JWT access tokens,
the token endpoint, and registered-client repositories. Authentication will provide a custom durable
`RegisteredClientRepository` adapter over the approved `oauth_clients` and
`oauth_client_scopes` logical model. Client secrets are encoded before persistence. Client and scope
records are durable; short-lived self-contained access tokens and their authorized-client cache need
not be durable because no refresh token is issued and resource servers validate JWTs locally.

The existing RS256 key/JWKS mechanism is reused. A token customizer sets:

- `typ=at+jwt`;
- `sub` to the registered service client ID;
- `aud=flash-sale-internal-api`;
- only validated requested scopes;
- a maximum five-minute access-token lifetime.

Only `POST /oauth2/token` with Client Credentials is a supported Feature 017 protocol. OIDC,
Authorization Code, consent, dynamic registration, token relay, and browser login are not added.
OAuth protocol failures retain standard OAuth error bodies rather than a Campaign/common-web
envelope.

For local provisioning, Authentication receives the two raw bootstrap secrets through runtime
secret configuration, encodes them, and creates the missing fixed client registrations without
logging secret values. Existing records are not silently overwritten; explicit secret rotation is
a later operational feature.

**Why framework support**: Spring Boot documents its Authorization Server integration and recommends
a durable/custom `RegisteredClientRepository` beyond development-only in-memory registration.
Spring Authorization Server supplies the standard token request validation and error handling rather
than recreating a security protocol in a controller.

**References**:

- [Spring Boot 3.5 OAuth2/Authorization Server](https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html)
- [Spring Authorization Server protocol endpoints](https://docs.spring.io/spring-authorization-server/reference/protocol-endpoints.html)
- [RegisteredClientRepository contract](https://docs.spring.io/spring-authorization-server/reference/api/java/org/springframework/security/oauth2/server/authorization/client/RegisteredClientRepository.html)

## R4. Campaign service-token acquisition

**Decision**: Add Spring Security OAuth2 Client to Campaign Service and use
`AuthorizedClientServiceOAuth2AuthorizedClientManager` with only the Client Credentials provider.

That manager is designed for service/background execution outside an `HttpServletRequest`. Its
in-memory authorized-client service satisfies the approved in-process cache rule: reuse an unexpired
token, renew near expiry, never persist/log the token, and allow scheduler/recovery work without an
administrator session.

Two Campaign registrations are configured:

| Registration ID | Requested scope | Used by |
|---|---|---|
| `campaign-product` | `catalog.read` | Product validation adapter |
| `campaign-inventory-allocation` | `inventory.campaign.allocate` | Inventory allocation adapter |

Both use `CAMPAIGN_CLIENT_ID` and `CAMPAIGN_CLIENT_SECRET`; separating registration IDs separates the
cached tokens and prevents unnecessary scope aggregation.

**Reference**: [Spring Security service-application authorized client manager](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/client/core.html)

## R5. Audience and endpoint isolation

**Decision**: Resource services use ordered `SecurityFilterChain` instances with endpoint-specific
JWT decoders/validators.

- public/admin chain: existing issuer, `aud=flash-sale-api`, existing user authorities;
- internal Campaign integration chain: existing issuer/JWKS, `aud=flash-sale-internal-api`, required
  service subject, and narrow endpoint scope;
- final fallback chain remains deny-by-default or preserves already-approved public endpoints.

Campaign's internal snapshot chain requires subject `flashsale-service` and
`SCOPE_campaign.snapshot.read`. Product campaign validation and Inventory allocation require subject
`campaign-service` plus their respective scopes. The audiences are never accepted interchangeably.

Spring Security supports multiple `HttpSecurity` instances selected by `securityMatcher`; ordered
chains are a smaller and more explicit design than a global decoder accepting both audiences.

**References**:

- [Spring Security multiple HttpSecurity instances](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)
- [Spring Security resource-server JWT audience validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

## R6. HTTP response contract

**Decision**: Preserve the approved Campaign source contract: Campaign success responses are direct
DTOs with HTTP `Location`/`ETag` headers where specified, and Campaign owns its error body containing
`code`, `message`, `traceId`, and optional `fieldErrors`.

This does not create a shared business contract. Inventory compatibility still consumes Inventory's
existing `ApiResponse<AllocationResponse>` envelope and maps `data.id` to Campaign's
`inventoryAllocationId`. OAuth token responses use the standard OAuth schema. The response choice is
documented before implementation and does not retrofit existing Product/Auth contracts.

## R7. Persistence and concurrency

**Decision**: PostgreSQL is the only Campaign durable store; JPA uses short local transactions and
Liquibase owns schema evolution.

- `@Version` protects draft mutations and lifecycle transitions.
- a unique `(campaign_id, idempotency_key)` plus an active-operation partial unique index prevents
  duplicate schedule workflows;
- the request fingerprint is a SHA-256 digest of a canonical representation of campaign ID, expected
  version, and current configuration;
- the stable Inventory request UUID is created once with the schedule operation and never replaced;
- Product and Inventory calls occur after the operation transaction commits and before the final
  Campaign transaction begins;
- lifecycle workers use conditional status/version/time updates rather than a distributed lock;
- database time/UTC `Clock` semantics are kept explicit in tests.

The operation row is retained indefinitely. `FAILED` represents a known business rejection and may
be reopened only by the same key/hash/version/configuration while retaining the Inventory request
ID; ambiguous remote failures remain resumable without first becoming terminal.

**Rejected**: a long transaction around remote HTTP, a distributed lock, cross-service database
reads, and Redis in Campaign Service.

## R8. Transactional outbox and Kafka

**Decision**: Persist each scheduled/activated event in the same PostgreSQL transaction as its
Campaign transition; publish later as Avro SpecificRecords to `campaign.lifecycle.v1` with
`campaignId` as Kafka key. The Feature 017 amendment selects `TopicRecordNameStrategy`,
`BACKWARD_TRANSITIVE`, controlled registration, and `auto.register.schemas=false`.

The publisher claims due rows in a short transaction using a processing lease, sends outside the
claim transaction, then marks the same row published or schedules its next retry. Only the earliest
unpublished aggregate version may be claimed, so a failed Scheduled event blocks Activated for that
Campaign until it is published or explicitly requeued. A crash after Kafka acknowledgement but before
the database update may redeliver the same `eventId`; consumers must deduplicate by event ID.

Producer properties explicitly keep idempotence enabled with `acks=all`. Kafka producer idempotence
reduces broker-side duplicates/reordering but does not replace outbox event identity or consumer
idempotency.

Local topic properties are three partitions and replication factor one. Shared local topic
provisioning belongs under `infra/docker`; service-owned producer configuration stays in Campaign.
Kubernetes topic provisioning is documented as a future environment concern rather than placed in
the service module.

**References**:

- [Spring Kafka `KafkaTemplate`](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/core/KafkaTemplate.html)
- [Apache Kafka producer idempotence and ordering](https://kafka.apache.org/40/configuration/producer-configs/)
- [Spring Kafka testing](https://docs.spring.io/spring-kafka/reference/testing.html)

## R9. Scheduler and retry policy

**Decision**:

- lifecycle scan fixed delay: two seconds;
- lifecycle batch size: 100;
- outbox scan fixed delay: 500 milliseconds;
- outbox batch size: 100;
- maximum automatic publication attempts: ten;
- backoff: `min(60 seconds, 2^retryCount seconds)`;
- after ten failures: `FAILED` until an authorized operator requeues the same event ID;
- requeue resets the automatic-attempt counter, increments a requeue counter, and records actor/time;
- no DLT, Debezium, or Campaign-ended event in this MVP.

All values are declarative properties and validation fails fast for non-positive intervals/batch
sizes or an invalid retry limit.

## R10. Observability

**Decision**: Keep Actuator/Prometheus declarative auto-configuration. Preserve `X-Trace-Id` at
HTTP boundaries, and propagate W3C `traceparent`/`tracestate` through Kafka headers, outbox relay,
schedulers, and structured logs. Trace context is not an Avro business field.

Important metrics include schedule results/latency, downstream failures, lifecycle transition
counts, outbox pending/failed/oldest-age, publication attempts, and recovery/requeue counts. Business
code depends on an application metrics port or semantic recorder, not a Prometheus registry.

Micrometer Tracing/OpenTelemetry export remains a later cross-service observability feature. Feature
017 keeps names and propagation boundaries compatible but does not add a partial OTLP stack.

## R11. Verification strategy

| Layer | Scope |
|---|---|
| Domain unit | invariants, lifecycle, immutable snapshot, price/quantity/limit policies |
| Application unit | commands, ports, idempotency fingerprint/replay, failure mapping |
| MVC/security | status/headers/body, validation, 401/403, two-audience token substitution |
| PostgreSQL Testcontainers | Liquibase, constraints, optimistic races, active-operation uniqueness, scheduler/outbox claims |
| HTTP contract | Gateway forwarding; Campaign/Product/Inventory/Auth request/response compatibility |
| Kafka integration | real broker/Registry-compatible test proves SpecificRecord schema, key, ordering, retry, duplicate identity, and W3C headers |
| Recovery/concurrency | concurrent schedule and activation; crash after allocation; publisher reclaim |
| Local smoke | Client -> Gateway -> Auth -> Campaign -> Product/Inventory -> PostgreSQL/Kafka |
| Module/reactor | affected-module verify then full `clean verify` |

Purchase-path load testing is omitted because the hot path is out of scope. A bounded concurrency
profile for schedule/lifecycle workers is included because correctness under races is in scope.

## New production dependencies and justification

| Module | Dependency | Reason |
|---|---|---|
| Campaign | `spring-boot-starter-validation` | request/property fail-fast validation |
| Campaign | `spring-boot-starter-data-jpa` | Campaign-owned PostgreSQL persistence |
| Campaign | `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | administrator/internal JWT validation |
| Campaign | `spring-boot-starter-oauth2-client` | standards-based Client Credentials acquisition/cache |
| Campaign | `org.mapstruct:mapstruct` | explicit web/persistence boundary mapping |
| Campaign | PostgreSQL runtime driver | Campaign database |
| Campaign | `spring-kafka` | outbox publication after the Kafka slice begins |
| Root `contracts/kafka-avro-contracts` | Apache Avro code generation + Confluent serializer/Registry tooling | generated SpecificRecord protocol artifact and compatibility gates; protocol-only, no domain types |
| Authentication | `spring-boot-starter-oauth2-authorization-server` | standards-based Client Credentials token endpoint |

Test-only additions are Spring Security Test, Spring Kafka Test, Spring Boot Testcontainers,
Testcontainers JUnit/PostgreSQL/Kafka, and ArchUnit if the repository's existing architecture-test
style is extended. Product, Inventory, and Gateway require no new production dependency for their
Feature 017 compatibility changes.

Campaign does not add `libs/common-web`: the approved Campaign source owns a direct success DTO and
Campaign-specific error/trace shape. Inventory's adapter consumes Inventory's existing common-web
envelope as a transport DTO without sharing Inventory business types.
