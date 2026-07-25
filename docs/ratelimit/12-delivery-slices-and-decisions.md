# 12 - Delivery Slices and Decision Register

**Document status**: Approved living design index for Feature 013  
**Canonical sources**: [spec](../../specs/013-gateway-redis-rate-limiter/spec.md),
[plan](../../specs/013-gateway-redis-rate-limiter/plan.md), [tasks](../../specs/013-gateway-redis-rate-limiter/tasks.md)

## 1. Purpose

This file explains how the approved work is sliced and where the resolved decisions live. It does
not replace Spec Kit artifacts, contracts, or ADRs.

## 2. Delivery Slices

| Slice | Main outcome | Task range |
|-------|--------------|------------|
| Artifact readiness | Approved spec, contracts, ADR, plan, and task graph | T001-T006 |
| Foundation | Dependencies, default-off config, pure policy/seam types | T007-T011 |
| US1 atomic catalog quota | Redis atomicity and Gateway-owned 429 | T012-T021 |
| US2 HTTP and identity boundary | HMAC/direct-IP security and pass-through compatibility | T022-T028 |
| US3 safe degradation | Typed fail-open, health, metrics, local Compose | T029-T038 |
| Release readiness | k6, converge, module/full validation, evidence, final status | T039-T051 |

## 3. Resolved Decision Register

| ID | Decision | Canonical owner |
|----|----------|-----------------|
| RL-D01 | Protect `product-catalog` GET only. | Feature 013 spec FR-001 |
| RL-D02 | Use direct socket `CLIENT_IP` identity for local MVP. | Spec FR-012-FR-014 |
| RL-D03 | Ignore forwarding headers; trusted proxy/Kubernetes deferred. | Spec FR-012, docs 11 |
| RL-D04 | Use quota `60` capacity, `30/1s` refill, request cost `1`. | Spec FR-003 |
| RL-D05 | TTL is twice full-refill, clamped `60s..24h`; MVP TTL is `60s`; rejected requests do not refresh. | Spec FR-020 |
| RL-D06 | Redis acquisition timeout is `50ms`. | Spec FR-016 |
| RL-D07 | Runtime coordinator failure mode is `ALLOW_WITH_METRIC`. | Spec FR-017-FR-018 |
| RL-D08 | Do not add limiter 503 in Feature 013. | Spec FR-018 |
| RL-D09 | Owned 429 emits `Retry-After` and `Cache-Control: no-store`; no accounting headers. | HTTP contract |
| RL-D10 | Limiter is disabled by default; explicit enablement required. | Config contract |
| RL-D11 | HMAC-SHA-256 lowercase hex with dedicated Base64 secret >=32 decoded bytes. | Spec FR-021 |
| RL-D12 | Missing state starts full; invalid state fails open; `PTTL=-1` receives expiry-only normalization. | Spec FR-019-FR-023 |
| RL-D13 | Spring Data owns `EVALSHA` to `EVAL` script-cache recovery. | Research Decision 2 |
| RL-D14 | Add Micrometer metrics/Observation hooks only; OTel runtime deferred. | Spec FR-025 |
| RL-D15 | Required gates include unit, real Redis, HTTP contract, failure, load measurement, module/full verify. | Plan verification |

## 4. Approval Flow

```text
Approved spec and contracts
  -> Accepted ADR 0004
    -> Approved plan and tasks
      -> T004-T006 living docs synchronization
        -> T007 production setup
          -> implement one approved slice at a time
            -> record validation evidence
              -> final Verified status
```

## 5. Deferred Expansion Queue

- authenticated user/API-key policies;
- admin/write fail-closed policies;
- login/pre-auth protection;
- multiple or hierarchical buckets;
- circuit breaker;
- dashboards and alert thresholds;
- Micrometer Tracing/OpenTelemetry runtime;
- Kubernetes trusted proxy and Redis HA topology;
- production load tuning and SLOs.
