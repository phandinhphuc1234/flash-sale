# Research: Gateway Rate-Limit Error Contract

## Decision 1: Keep the 429 contract additive and limiter-independent

**Decision**: Add one static code/status/message row and reuse the established Gateway writer. Do not
create a filter, handler, Redis integration, quota header, or retry policy in this feature.

**Rationale**: A stable public rejection contract can safely precede limiter activation, while quota,
window, key, and backend-failure decisions materially affect security and traffic behavior and may
not be inferred.

**Alternatives considered**:

- Enable Spring Cloud Gateway's default rate limiter now: rejected because quota/key/backend policy is unapproved.
- Add an empty `ratelimit` class: rejected because the repository creates packages/classes only with a real responsibility.

## Decision 2: `Retry-After` remains deferred

**Decision**: The 429 contract does not require or fabricate `Retry-After` in this slice.

**Rationale**: A meaningful value depends on the approved limiting algorithm and window. HTTP 429 can
be represented without inventing that duration. A later rate-limit feature must amend the contract
before adding any accounting or retry header.

## Decision 3: Separate correlation from distributed tracing

**Decision**: Preserve Feature 011's current error `traceId` behavior for this additive code, while
documenting that it is presently a correlation identifier rather than proof that OpenTelemetry
distributed tracing is installed.

**Rationale**: The repository has Actuator and Prometheus metrics but currently has no Micrometer
Tracing OpenTelemetry bridge, OTLP exporter, tracing configuration, or Collector deployment. Claiming
otherwise would make documentation drift from runtime behavior.

## Decision 4: Standardize future tracing on Micrometer Tracing plus OpenTelemetry

**Decision**: Spring application/Gateway code will use Micrometer Tracing abstractions. Spring Boot
auto-configuration will connect them to the OpenTelemetry bridge and an OTLP exporter; services will
export to a root-owned OpenTelemetry Collector using W3C trace context. Direct OpenTelemetry SDK use
inside business or Gateway policy code is not the default.

**Rationale**: Micrometer provides the vendor-neutral application facade integrated by Spring Boot.
The bridge creates OpenTelemetry-backed spans, the exporter sends OTLP, and the Collector is separate
runtime infrastructure that batches/processes/routes telemetry. This keeps instrumentation replaceable
and preserves repository infrastructure ownership.

**Runtime dependency candidates for the later tracing feature**:

- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`

No dependency is added by Feature 012.

**Official references**:

- [Spring Boot 3.5 Tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
- [Spring Boot application properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [Micrometer Tracing configuration](https://docs.micrometer.io/tracing/reference/configuring.html)
- [OpenTelemetry context propagation](https://opentelemetry.io/docs/concepts/context-propagation/)
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)

## Decision 5: Do not use custom `X-Trace-Id` as W3C propagation

**Decision**: A later tracing feature will use W3C `traceparent`/`tracestate` for distributed context.
The current `X-Trace-Id` remains a compatibility correlation header until a separately approved
contract migration decides whether to retain or rename it.

**Rationale**: Manually copying a custom correlation header does not create parent/child spans. When
runtime tracing exists, an error body should prefer the active Micrometer span's trace ID and use the
existing safe correlation resolver only when no span exists, such as very early failures or isolated tests.
