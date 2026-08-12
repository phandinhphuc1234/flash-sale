---
name: implement-service-http-openfeign
description: Design, implement, refactor, or review synchronous service-to-service HTTP communication with Spring Cloud OpenFeign in this Flash Sale Java 21 and Spring Boot 3 monorepo. Use when adding Feign clients, outbound HTTP ports/adapters, OAuth2 Client Credentials token propagation, internal headers, timeout/error/retry behavior, contract tests, or when deciding whether an interaction belongs in OpenFeign rather than Kafka, Redis Lua, Gateway routing, or gRPC.
---

# Implement Service HTTP with OpenFeign

Build synchronous internal HTTP calls without leaking OpenFeign, OAuth, or remote DTOs into the
application/domain core. Treat OpenFeign as an outbound adapter implementation, not as the service
contract or architecture itself.

Read [project-openfeign-patterns.md](references/project-openfeign-patterns.md) before adding or
changing dependencies, Feign configuration, security headers, error mapping, retry, or tests.

## Load governing context

Before production changes:

1. Read `AGENTS.md`, `.specify/memory/constitution.md`, and the active feature's approved `spec.md`,
   `plan.md`, `tasks.md`, HTTP contracts, and ADRs.
2. Confirm `.specify/feature.json` or `SPECIFY_FEATURE_DIRECTORY` points to that feature.
3. Verify the plan explicitly approves OpenFeign and
   `org.springframework.cloud:spring-cloud-starter-openfeign` for the calling service.
4. Stop if authentication, timeout, retry, idempotency, error, or fallback behavior is unresolved.
   Do not infer these policies.

For design or review, read `docs/architecture/service-communication-protocols.md` and also apply
`$apply-clean-hex-architecture`. For a response-contract change, also use
`$standardize-api-response-error-handling`.

## Decide whether OpenFeign fits

Use OpenFeign only when the caller needs an immediate answer through an approved HTTP/JSON
contract and a blocking call fits the service's runtime model.

Use another mechanism when appropriate:

- Use Gateway routing for external client ingress; never call downstream services with Feign from
  the WebFlux Gateway event loop.
- Use Kafka for asynchronous workflow handoff, integration events, buffering, and fan-out.
- Use Redis Lua only for the atomic flash-sale reservation hot path.
- Use an approved gRPC contract only when a later ADR/feature selects gRPC.
- Add a batch HTTP contract instead of invoking Feign repeatedly in a loop.

For the current Campaign MVP, approved candidates are Campaign-to-Product validation and
Campaign-to-Inventory allocation. Do not move Product, Inventory, Order, or Payment calls into the
per-purchase hot path.

## Keep the boundary clean

Use this dependency direction:

```text
application use case
  -> capability-oriented output port
  <- outbound client adapter
       -> Feign interface + remote DTOs + remote-error decoder
configuration
  -> Feign/OAuth2/timeout wiring
```

Place types under the owning feature:

```text
<feature>/
├── application/port/out/<BusinessCapability>Port.java
└── adapter/out/client/<downstream>/
    ├── <Downstream>FeignClient.java
    ├── <Downstream>ClientAdapter.java
    ├── dto/
    ├── mapper/             # only when mapping complexity justifies it
    └── error/              # only when dedicated translation is needed

<service>/configuration/   # shared Feign, OAuth2, timeout, and observation wiring
```

Do not create empty packages. Keep `@FeignClient`, Feign exceptions, HTTP headers/statuses, remote
DTOs, OAuth tokens, and client configuration out of application ports and domain code. Name output
ports after business capabilities, never `HttpPostPort` or `FeignClientPort`.

## Implement one approved interaction

1. Identify the caller, contract owner, method, path, request, response, stable errors, and rollout
   order.
2. Define an application output port using caller-owned commands/results or domain values.
3. Define narrow remote request/response DTOs beside the Feign interface. Never import another
   service's JPA entity or domain model.
4. Define a dedicated Feign interface for the downstream capability and configure its base URL from
   service-owned properties. Use Docker Compose/Kubernetes DNS names; do not add Eureka.
5. Implement an adapter that maps internal models to wire DTOs, invokes Feign, verifies response
   identity when required, and maps remote failures into application-owned outcomes.
6. Configure authentication, trace propagation, explicit timeouts, safe logging, and any approved
   idempotency header/request identity.
7. Test the application against the output port and test the adapter against an HTTP stub. Do not
   mock Feign internals in application tests.

## Preserve service identity and tracing

- Never relay an administrator/end-user token unless the approved contract explicitly requires user
  delegation. Feature 017 uses OAuth2 Client Credentials for `campaign-service`.
- Request only the endpoint-specific scope. Do not reuse a broad token when narrower Product and
  Inventory registrations exist.
- Ensure the receiving service independently verifies signature, issuer, expiry, audience, subject,
  and scope.
- Propagate the repository's tracing context. Preserve `X-Trace-Id` where the approved contract
  requires it and allow Micrometer/OpenTelemetry instrumentation to propagate W3C trace context.
- Never log bearer tokens, client secrets, cookies, full sensitive bodies, or authorization headers.

## Bound failure behavior

- Set explicit connect and read timeouts inside the caller's approved end-to-end latency budget.
- Do not let `FeignException`, decoder types, or raw downstream messages escape the outbound
  adapter.
- Map by HTTP status plus stable remote error code, never by human message text.
- Disable automatic retry unless the approved plan defines retryable failures, maximum attempts,
  backoff, and idempotency behavior.
- Reuse the same idempotency key or durable request identity on an approved retry; never generate a
  replacement after an ambiguous outcome.
- Do not add Resilience4j, a circuit breaker, bulkhead, or fallback dependency unless the active
  plan/ADR approves it. Never return invented price, stock, or product data as a fallback.
- Keep remote calls outside database transactions unless an approved plan proves otherwise.

## Verify the boundary

Cover at least the risks applicable to the interaction:

- exact method, path, query, JSON body, content type, and response mapping;
- service token, audience/subject/scope isolation, no user-token relay, and secret redaction;
- `X-Trace-Id` and W3C trace-context propagation according to the active contract;
- documented 4xx/5xx, stable error codes, malformed responses, timeouts, and connection failures;
- idempotent replay and unchanged request identity for commands;
- response identity checks so one Campaign/Variant/allocation cannot be accepted for another;
- no Feign or remote DTO dependency in application/domain packages.

Run the affected module command from the approved plan, normally:

```powershell
.\mvnw.cmd -pl services/<caller-service> -am verify
```

Record the command, scope, result, and remaining risk before checking the task complete.

## Completion checklist

- [ ] OpenFeign and its dependency are approved by the active plan/ADR.
- [ ] A documented HTTP contract exists and names its owner/caller.
- [ ] Application calls a capability port; only the outbound adapter knows Feign.
- [ ] Remote DTOs and failures are translated at the adapter boundary.
- [ ] Service identity, least-privilege scope, trace headers, and secrets are handled safely.
- [ ] Timeouts and retry/idempotency behavior match the approved contract.
- [ ] No blocking Feign call enters Gateway WebFlux or the flash-sale purchase hot path.
- [ ] Contract/integration tests and required Maven verification pass with evidence recorded.
