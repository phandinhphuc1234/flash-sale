# Service Communication Protocols

## Purpose and authority

This document defines how to choose a communication mechanism in the Flash Sale Engine. It
explains repository-wide rules; it is not a business API contract and does not approve any route,
topic, event schema, or integration by itself.

The order of authority is:

1. `.specify/memory/constitution.md`
2. An approved feature `spec.md`, `plan.md`, and its contract files
3. An accepted ADR for an architectural exception or change
4. This guide

Use these status labels consistently:

- **DECIDED**: required or permitted by the Constitution.
- **CURRENT**: implemented in the repository now.
- **PLANNED**: direction is decided, but the business integration is not implemented.
- **DEFERRED**: not part of the approved baseline; it needs new architectural approval.
- **NOT ADOPTED**: no current decision authorizes its use.

## Repository-wide rules

- Public traffic enters through `api-gateway`.
- Synchronous cross-service communication uses an explicit, documented HTTP contract.
- Asynchronous cross-service communication uses a documented, versioned Kafka contract.
- A service never reads another service's database or imports another service's domain model as a
  substitute for communication.
- Important requests and events propagate a trace ID through gateway, HTTP, Kafka, and logs.
- Redis is not a service-to-service transport and is not durable business truth.
- A change to the communication style requires an approved ADR. A rule that conflicts with the
  Constitution also requires a Constitution amendment; an ADR alone cannot override it.

## Protocol decision matrix

| Mechanism | Use it for | Do not use it for | Repository status |
|---|---|---|---|
| HTTP/JSON | Request/response work that needs an immediate result | Durable workflow handoff, event fan-out, or long synchronous call chains | **DECIDED**; service shells are **CURRENT**, business contracts and routes are **PLANNED** |
| Kafka | Asynchronous workflows, integration events, load buffering, and fan-out | Synchronous queries, token validation, or RPC disguised as messaging | **DECIDED**; local broker is **CURRENT**, business contracts and adapters are **PLANNED** |
| Redis Lua | Atomic flash-sale hot-path decisions such as reservation or quota checks | General messaging, workflow orchestration, or durable source of truth | Constraint is **DECIDED**; business model and scripts are **PLANNED** |
| gRPC | No approved use case yet | The default protocol for every internal request/response call | **DEFERRED** |
| WebSocket | A future client-facing realtime feature such as chat or order-status push, if justified | Service-to-service RPC, durable messaging, or replacing Kafka | **NOT ADOPTED** |
| RabbitMQ | No approved use case | A second broker added only because it is familiar | **NOT ADOPTED**; Kafka is the selected asynchronous baseline |

## 1. Client to API Gateway

Use an HTTP API with JSON for web and mobile clients. Production traffic must use TLS. Local
development may use plain HTTP.

The application contract must not require HTTP/1.1 specifically. HTTP/1.1, HTTP/2, or another
supported transport version may be negotiated by the client, ingress, load balancer, or gateway
without changing the REST contract. Version the application API through its documented contract,
not through the transport protocol version.

Examples of future public contracts include:

```text
GET  /api/v1/products/{productId}
POST /api/v1/auth/login
POST /api/v1/flash-sales/{campaignId}/purchase-requests
```

These examples are illustrative only. A route is not approved until an active feature owns its
contract and tests.

### Gateway responsibility

The gateway may own routing, coarse route authorization, JWT verification, CORS, security headers,
rate limiting, trace propagation, and edge error normalization. It must not own order, payment,
stock, campaign, or user business workflows.

## 2. JWT and identity propagation

`authentication-service` owns registration, login, refresh, token issuance, signing-key lifecycle,
and authentication policy. The gateway is the planned validation point for external bearer tokens.

For a signed JWT, the gateway should validate the signature and required claims locally with a
trusted public key or JWK set. It should not make a synchronous call to `authentication-service`
for every request merely to validate the token. Per-request remote validation adds latency and
makes authentication-service availability part of every public request path.

The future security feature must define:

- accepted issuer and audience
- signature algorithm and signing-key/JWK rotation
- expiry, not-before, clock-skew, and revocation behavior
- public versus protected route rules
- whether downstream services revalidate the JWT, receive a token, or receive a trusted internal
  identity context
- service/workload identity; an end-user JWT must not be treated as workload identity

The gateway must remove or overwrite identity headers supplied by an external client. Business
services still enforce resource and business authorization; gateway authentication alone does not
prove that a user owns a particular order, payment, or campaign.

Never put a JWT, refresh token, password, signing secret, or raw authorization header in Kafka
records, logs, traces, metrics, or OpenTelemetry baggage.

## 3. Synchronous internal communication with HTTP

Use HTTP when the caller cannot continue without an immediate answer. Typical future examples are
reading a product snapshot while preparing a campaign or retrieving an order status.

In Spring MVC services, prefer a service-owned outbound adapter backed by `RestClient` or a Spring
HTTP Interface. A reactive caller may use `WebClient`. The application layer depends on a
capability-oriented output port; domain code does not depend on either HTTP client.

Each HTTP interaction must define before implementation:

- contract owner, method, path, request, response, and error schema
- service base URL supplied by configuration
- connect timeout, response timeout, and end-to-end latency budget
- idempotency behavior for commands
- which failures are retryable and the bounded retry policy
- trace and identity propagation
- compatibility and rollout expectations
- contract and integration tests

Use Docker Compose service names locally and Kubernetes Service DNS in the cluster. Do not use pod
IP addresses or introduce Eureka. Keep synchronous dependency chains short; a request path such as
`gateway -> service A -> service B -> service C` increases latency and cascading-failure risk.

Retries are not a substitute for availability. Do not automatically retry a non-idempotent command
unless the contract defines an idempotency key and duplicate behavior. A business rejection is not
a transient transport failure and must not be retried indefinitely.

## 4. Asynchronous communication with Kafka

Use Kafka when a fact or accepted request should be processed independently, when multiple services
need the result, or when the system must absorb traffic spikes without keeping the client request
open.

Every Kafka contract must define:

- event or command meaning and owning producer
- topic ownership and authorized consumers
- schema version and compatibility policy
- event ID, trace ID, occurrence time, aggregate ID, and schema version
- partition key and the exact scope in which ordering is required
- idempotency/deduplication key and retention lifecycle
- retry/backoff, poison-record handling, dead-letter or parking strategy, and replay procedure
- rollout order and contract tests

Kafka ordering is only guaranteed within a partition. Events that require per-order ordering should
normally use the stable order or reservation ID as their key; the feature plan must prove the
chosen key and partitioning model.

Consumers must be idempotent because records can be redelivered. Do not describe the business flow
as "exactly once" unless the complete business effect, including writes to external systems, has
been proven. Kafka transaction guarantees do not make a PostgreSQL write and Kafka publication one
atomic transaction.

When a durable database change must be followed by a required event, persist the business change
and an outbox record in the same local database transaction. An outbox publisher then sends the
record to Kafka. The plan must define retry, cleanup, deduplication, monitoring, and recovery.

### 4.1 Schema Registry status

The local Compose topology includes Confluent Schema Registry at `http://localhost:8081`. It is a
platform capability for future Avro, Protobuf, or JSON Schema validation; it does not itself approve
an event contract, replace the repository contract files, or remove the need for an outbox and
idempotent consumers. A feature must define schema format, subject naming, compatibility mode,
producer/consumer ownership, and rollout tests before adding a service serializer or deserializer.

## 5. Why gRPC is deferred

gRPC is a valid technology, but "internal and fast" is not enough evidence to make it the default.
It adds Protocol Buffer contracts, code generation, compatibility rules, deadlines, channel
management, security, observability, testing, and operational knowledge alongside the existing
HTTP stack.

The current Constitution requires synchronous cross-service communication to use documented HTTP
contracts. Therefore a gRPC implementation requires all of the following before code or a new
dependency is added:

1. A measured use case where HTTP does not meet an explicit latency, payload, CPU, or streaming
   requirement.
2. An approved feature spec and plan.
3. An ADR covering alternatives and migration consequences.
4. A Constitution amendment or clarification that explicitly permits the selected gRPC use case.
5. A `.proto` ownership and compatibility policy, generated-code location, deadline and security
   model, contract tests, and load-test evidence.

Do not use gRPC to validate every JWT. Local cryptographic JWT validation removes that network hop
entirely.

## 6. WebSocket is a separate realtime decision

WebSocket may later be appropriate for chat, presence, or client order-status push. It does not
replace Kafka and does not make messages durable. A WebSocket feature must define gateway routing,
authentication, reconnect/resume behavior, backpressure, horizontal scaling, presence ownership,
message persistence, and load tests. Public WebSocket traffic must still enter through the gateway.

Until such a feature and any required ADR are approved, clients should obtain asynchronous workflow
status through a documented HTTP query or polling contract.

## 7. Corrected flash-sale reference flow

The following is a target interaction shape, not an approved event contract:

```text
Client
  -> api-gateway
  -> flashsale-service                         synchronous HTTP
  -> Redis Lua atomic reservation              hot-path decision
  -> durable acceptance / recovery boundary    must be specified
  <- 202 Accepted + request/reservation ID

flashsale-service
  -> transactional outbox when applicable
  -> Kafka: illustrative order.requested.v1
  -> order-service creates its own pending order idempotently
  -> Kafka: illustrative order.created.v1 or order.failed.v1
  -> payment-service performs an idempotent provider operation
  -> Kafka: illustrative payment.succeeded.v1 or payment.failed.v1
  -> notification-service reacts to approved outcome events
```

Important corrections:

- `flashsale-service` must not publish "order created" because `order-service` owns order creation.
  It may publish an accepted purchase/order request after the future contract defines that fact.
- A `202 Accepted` response is safe only after the request has reached the durable or recoverable
  acceptance boundary defined by the feature. Returning success after only an in-memory handoff can
  lose the request.
- Redis and PostgreSQL cannot share one local ACID transaction. The feature must define reservation
  expiry, persistence, reconciliation, compensation, stock release, and crash recovery.
- `order-service`, `payment-service`, and `notification-service` do not simply consume one message
  "sequentially and safely". Each owns its state transition and publishes/consumes explicit
  contracts. Ordering exists only inside the selected Kafka partition.
- Payment-provider calls require their own idempotency and reconciliation rules.
- The candidate names ending in `.v1` remain illustrative until an approved feature creates their
  schemas under its `contracts/` directory.

## 8. Artifact ownership

| Artifact | Location |
|---|---|
| User-visible behavior and failure outcomes | `specs/<feature>/spec.md` |
| Interaction choice, timeout/retry/outbox decisions | `specs/<feature>/plan.md` |
| Feature-local HTTP and Kafka contract design | `specs/<feature>/contracts/` |
| Accepted cross-feature HTTP catalog, when promoted | `contracts/openapi/` |
| Accepted channel/topology catalog, when promoted | `contracts/asyncapi/` |
| Accepted versioned event schemas, when promoted | `contracts/events/<domain>/` |
| Communication-style change or exception | `docs/adr/` |
| Controller, listener, client, publisher, and persistence adapter | owning `services/<service>/` module |
| Kafka/Redis local or cluster orchestration | root `infra/` |

Architecture diagrams may show planned candidate flows, but a diagram edge or event name is not a
contract. Implementation begins only after the owning feature's specification, plan, tasks, and
contracts are approved.

## Official references

- [Spring Framework REST clients and HTTP Interfaces](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html)
- [Spring Security reactive JWT resource server](https://docs.spring.io/spring-security/reference/6.5/reactive/oauth2/resource-server/jwt.html)
- [Apache Kafka concepts and partition ordering](https://kafka.apache.org/documentation/)
- [Apache Kafka delivery semantics](https://kafka.apache.org/41/design/design/)
- [gRPC core concepts](https://grpc.io/docs/what-is-grpc/core-concepts/)
- [Kubernetes DNS for Services and Pods](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- [Spring Cloud Gateway WebSocket routing](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/global-filters.html)
