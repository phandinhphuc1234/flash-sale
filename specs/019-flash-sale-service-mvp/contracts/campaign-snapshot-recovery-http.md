# Campaign Snapshot Recovery HTTP Contract

**Caller**: `flashsale-service`  
**Callee**: `campaign-service`  
**Purpose**: Repair a missing/stale Redis Campaign projection outside the shopper hot path

## Endpoint

```http
GET /internal/v1/campaigns/{campaignId}/snapshot
Authorization: Bearer <client-credentials-token>
Accept: application/json
traceparent: <W3C trace context>
```

This reuses the approved Feature 017 internal contract. It is called through an application output
port and an OpenFeign adapter, never through API Gateway and never from the ordinary reservation
request path.

## Service Identity

| Property | Required value |
|---|---|
| Client subject | `flashsale-service` |
| Grant | OAuth2 Client Credentials |
| Audience | `flash-sale-internal-api` |
| Scope | `campaign.snapshot.read` |
| Maximum token TTL | 300 seconds |

Campaign validates signature, issuer, audience, expiration, subject, and scope. The client secret is
provided through environment/secret management and is never committed or logged.

## Client Policy

| Setting | Value |
|---|---:|
| Connect timeout | 500 ms |
| Read timeout | 1,000 ms |
| Feign retryer | Never retry |
| Application retry owner | Projection recovery scheduler |
| Recovery scan interval | 5 seconds |

The call does not execute inside a Flash Sale database transaction. Only successful, validated,
newer snapshots are applied through the same atomic version guard used by Kafka projection updates.

## Response and Error Handling

The success payload remains the raw internal Campaign snapshot defined by Feature 017; it is not
wrapped with the public `ApiResponse`. The Feign adapter maps it into a service-owned application
result and must not leak Feign or HTTP types inward.

| Callee result | Flash Sale behavior |
|---|---|
| 200 valid snapshot | Apply atomically if newer; clear recovery marker. |
| 401/403 | Treat as configuration/security fault; keep projection closed and alert. |
| 404 | Keep projection unavailable; record sanitized reason and retry only by scheduler policy. |
| 409 | Snapshot not in recoverable sellable state; keep admission closed. |
| 429 | Honor `Retry-After` when scheduling the next recovery attempt. |
| 5xx/timeout/network | Keep state, reschedule with bounded backoff, expose dependency metric. |

No recovery failure is converted into a live hot-path call. Shopper requests continue to fail closed
with the public projection-unavailable contract until recovery completes.

