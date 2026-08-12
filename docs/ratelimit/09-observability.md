# 09 - Metrics, Logs, and Distributed Tracing

**Document status**: Approved living design for Feature 013  
**Canonical source**: [plan observability section](../../specs/013-gateway-redis-rate-limiter/plan.md)

## 1. Goal

Telemetry must let operators distinguish allowed, rejected, identity-unavailable, and fail-open
outcomes without creating high-cardinality data or leaking caller identity. Telemetry does not
replace acceptance, concurrency, or contract evidence.

## 2. Approved Metrics and Observation

Micrometer source names use dot notation; Prometheus converts names by convention.

| Name | Type | Bounded dimensions |
|------|------|--------------------|
| `gateway.rate.limit.decisions` | Counter | `policy`, `route`, `outcome` |
| `gateway.rate.limit.errors` | Counter | `policy`, `error_type` |
| `gateway.rate.limit.acquire` | Observation/timer | `policy`, `route`, `outcome`, `failure_mode`, controlled `error_type` |

Declarative local percentiles are enabled only for the acquisition timer:

```text
0.5, 0.95, 0.99
```

These are local measurement outputs, not a production SLO.

## 3. Approved Outcome Vocabulary

Use bounded values such as:

```text
allowed
rejected
identity_unavailable
fail_open
```

`error_type` comes only from the controlled failure categories:

```text
TIMEOUT | CONNECTION | INVALID_STATE | INVALID_RESULT | SCRIPT | SERIALIZATION
```

Do not tag with exception messages.

## 4. Forbidden Telemetry Data

Do not put these in metric tags, Observation high-cardinality values, trace attributes, baggage, or
normal logs:

- raw IP;
- user ID or subject;
- API-key ID or secret;
- JWT;
- HMAC secret;
- Redis bucket key or digest;
- trace ID as a metric tag;
- raw path, query, product ID, campaign ID, or other high-cardinality identifiers.

## 5. Logs

Feature 013 does not add limiter-specific per-request INFO/WARN logs for allowed, rejected,
identity-unavailable, or fail-open traffic. Bounded metrics and Observation are the normal signal.

Safe lifecycle/configuration logs may mention enablement, bounded policy ID, route ID, state
version, and failure mode. They must not echo secrets, raw identities, digests, Redis keys, queries,
or raw exception messages.

Expected Gateway-owned 429 should not invoke the generic per-error WARN path. Serialization fallback
and other unexpected Gateway errors remain owned by the central error boundary.

## 6. Trace Boundary

Feature 013 preserves the repository's current `traceId` correlation contract. It adds an
always-active catalog correlation filter that forwards one normalized/generated `X-Trace-Id` to the
Product catalog route and reuses the same exchange value in Gateway-owned 429 bodies.

This is not a claim that full distributed tracing is installed.

Future runtime tracing target:

```text
Spring Observation / Micrometer Tracing
  -> OpenTelemetry bridge/exporter
    -> root-owned OpenTelemetry Collector
      -> Tempo/Grafana
```

Gateway code must use Micrometer abstractions and must not call the OpenTelemetry SDK directly in
Feature 013.

## 7. Deferred Monitoring Assets

Dashboards, alerts, OTel Collector, Tempo, and threshold/SLO definitions are deferred. When added,
root-owned assets belong under `infra/monitoring`, not inside the Gateway module.
