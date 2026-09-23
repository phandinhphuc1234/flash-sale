# Implementation Plan: Shopper Purchase Presentation

**Feature**: `054-shopper-purchase-presentation` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Status**: Approved — owner approved plan and tasks on 2026-09-23

## Summary

Persist immutable Product/Variant names on new Order lines, return nullable names from the existing owner-only Order detail API, and render neutral labels for legacy or unavailable names. Regular purchases carry names from the validated Product quote through their durable recovery checkpoint; Flash Sale Order creation attempts a best-effort internal Product lookup without blocking saga creation. Frontend renders clearer status, price, and campaign-demo wording without changing purchase or payment behavior.

## Technical Context

- **Language/runtime**: Java 21, Spring Boot 3.x, JPA/Liquibase, PostgreSQL; Next.js 15/React 19/JavaScript.
- **Existing integration**: Order-only internal Product purchase-quote HTTP endpoint through OpenFeign/OAuth2 client credentials; `PurchaseAcceptedV1` Kafka consumer and Order inbox/outbox.
- **Storage**: `order_lines` with two new nullable name columns; `regular_purchase_requests.snapshot_payload` JSONB with optional name keys.
- **Testing**: Maven/JUnit/Testcontainers integration and contract tests; Node unit tests; Next production build; focused local browser review.
- **Target**: independently deployed Order service and local/cloud storefront through the existing Gateway.
- **Performance/scale constraint**: no Product call on Order GET or Flash Sale reservation hot path; one bounded, best-effort Product call during asynchronous Flash Sale Order creation. Do not invent a new latency SLO; compare existing Feign bounds and report evidence.
- **Scope**: one additive Order API response, one Order-owned migration, existing Product contract reuse, four shopper screens and a shared display helper.

## Constitution Check — pre-research and post-design: PASS for proposed design

| Principle | Design check |
|---|---|
| Spec/tasks | Owner resolved both P1 name decisions. No production edit until this plan/tasks approval and exact task selection. |
| Independent ownership | Order alone owns historical names, JPA and migration; Product database is never queried directly. No service module dependency. |
| Communication | Reuse documented internal Order-to-Product HTTP contract and existing Gateway route; no Kafka schema, producer or consumer payload change. Public Order response delta is documented first in `contracts/order-item-display-http.md`. |
| Durable truth and messaging | PostgreSQL owns snapshots. The regular request fingerprint and Flash Sale inbox/outbox/saga effects remain unchanged. No Redis Lua/stock behavior change. |
| Root infra | No shared infrastructure, Kustomize or runtime configuration changes. Migration remains in Order service. |
| Observability | Existing trace header/client credentials and Actuator endpoints remain. Product lookup fallback logs only safe failure category and correlation ID; do not print product/customer data. |
| Dependencies/ADR | No new dependency, service boundary, discovery, durability model or communication style. No ADR required. |
| Validation | Order module verify, full Maven verify because backend/frontend contract is cross-cutting, migration/contract/replay tests, frontend unit/build/browser checks. No load test because Redis hot path and purchase concurrency semantics are untouched; no Kubernetes dry-run because no manifests change. |

## Design and service ownership

1. `regularpurchase` calls Product as today and records optional `productName`/`variantName` in each durable intake line at `PRODUCT_VALIDATED`. The original request fingerprint hashes only browser-supplied fields, and old JSON lacking names is readable. All later copy/recovery states retain those names.
2. At regular Order acceptance, create `OrderLine` with the stored names, without a second Product call. This works after a process crash at `PRODUCT_VALIDATED` or `HOLD_ACQUIRED` and leaves price/quantity unchanged.
3. The Flash Sale `CreateOrderFromAcceptedPurchaseService` asks an Order-owned `LookupOrderLineNamesPort` for optional names before the existing persistence transaction. A Product adapter reuses the existing secured Feign purchase-quote transport, checks that response `variantId` matches the requested ID and names are usable, and returns empty on timeout, 4xx/5xx, malformed response or unsellable state rather than throwing into saga creation. Avoid catching failures from Order persistence, event validation, or saga logic.
4. Existing `OrderCreationJpaAdapter` inbox/purchase/reservation deduplication remains authoritative; names are excluded from the event fingerprint and Order outbox payload. Replay never updates an inserted Order line, including one created with null names.
5. `OrderLine` domain, `OrderLineJpaEntity`, Order query result and web mapper carry nullable names. Order details add `productName` and `variantName` while retaining the existing envelope, fields and owner-only security.
6. Frontend uses a display helper for known Order/Payment/Reservation statuses and a neutral unknown fallback. Payment action eligibility continues using canonical backend status, not translated labels. Product card derives a minimum displayable catalog price and labels it as a starting price for multiple variants. Product detail keeps selected-variant price, removes the unverified badge, and marks the campaign-ID control as manual/demo.

## Contracts and compatibility

- Proposed additive Order API delta: [contracts/order-item-display-http.md](contracts/order-item-display-http.md). Update accepted `docs/api/frontend-integration-guide.md` with the implementation and add contract tests.
- Existing private Product quote endpoint is unchanged. Do not broaden browser or service authorization. The Order client already has the required OAuth2 subject/scope and URL configuration.
- `PurchaseAcceptedV1` and Order event/outbox payloads remain unchanged. No Kafka schema registration or topic change.
- Rollout: apply nullable Order migration first; deploy new Order image; then deploy frontend. Old Order image can coexist with added nullable columns, and new frontend tolerates null/missing fields during rollout. Rollback of Order image does not remove columns or old/new rows.

## Data, failure and recovery

- New Liquibase `007-...sql` adds nullable `VARCHAR(255)` name fields to `order_lines`. Do not backfill or drop existing `display_name_snapshot`, `product_id`, or `sku_snapshot` fields.
- Regular intake JSON adds optional line name keys; legacy records decode to null. After validation, subsequent retries reuse the persisted names rather than querying Product again. Duplicate idempotency key behavior is unchanged.
- A failed Flash Sale name lookup returns null names and must not delay Order beyond the existing bounded HTTP timeout or alter Kafka retry/DLT semantics. A duplicate event can perform another lookup but persistence replays the original Order and never updates names.
- No new compensation, price, stock, payment, expiry or refund rule. Database errors still fail according to the existing Order consumer policy; name-lookup errors alone do not.

## Security and observability

The existing shopper principal and owner-only Order read path remain authoritative. The frontend never calls private Product APIs. The internal Feign client keeps service credentials server-side and propagates `X-Trace-Id`. A bounded warning/metric uses safe failure categories; no credentials, customer data, URLs, or raw provider errors in logs. Liveness/readiness/Prometheus configuration is unchanged.

## Verification strategy

| Behavior | Evidence |
|---|---|
| Regular names and recovery | Unit tests of fingerprint/transition; integration tests with `snapshot_payload` across `PRODUCT_VALIDATED` and `HOLD_ACQUIRED`, including legacy JSON. |
| Flash Sale best-effort and replay | Use-case tests for found/missing/failure; consumer/persistence integration tests proving saga creation and no snapshot overwrite on replay. |
| Migration/API compatibility | Liquibase migration test on old rows; Order HTTP contract test for named/unnamed lines and unchanged owner restriction, envelope, money and trace behavior. |
| Shopper wording | Node tests for status labels, unknown fallback and card price selection; Next build and desktop/mobile local browser review. |
| Required gates | `.\mvnw.cmd -pl services/order-service -am verify`, `.\mvnw.cmd clean verify`, `npm.cmd --prefix 'flash-sale frontend/QuickCart' run build`, relevant Node tests, `git diff --check`. Record command, scope and exit/result in `validation.md`. |

Integration and contract tests are required due to Order schema/API and Kafka-consumer-adjacent logic. A separate load run is not required because this feature does not touch the flash reservation hot path or its concurrency rules. No K8s dry-run applies without manifest changes.

## Project structure

```text
specs/054-shopper-purchase-presentation/{spec,plan,research,data-model,quickstart,tasks,validation}.md
specs/054-shopper-purchase-presentation/contracts/order-item-display-http.md
services/order-service/src/main/resources/db/changelog/{db.changelog-master.yaml,changes/007-add-order-line-name-snapshots.sql}
services/order-service/src/main/java/com/philia/flashsale/order/{order,regularpurchase}/...  # existing feature-local boundaries
services/order-service/src/test/java/com/philia/flashsale/order/{order,regularpurchase}/...
docs/api/frontend-integration-guide.md
flash-sale frontend/QuickCart/{lib,components,app,tests}/...
```

**Structure decision**: Extend existing Order and regular-purchase feature packages; add no shared Java domain model, root infrastructure, or new service. The frontend helper belongs in its existing `lib/` area rather than duplicating wording across screens.

## Complexity tracking

No constitutional exception. The asynchronous Flash Sale display lookup reuses an existing Order-to-Product HTTP channel rather than introducing a new event or service boundary; it is outside the Redis hot path and is explicitly best-effort per the owner decision.

## Review gate

The owner approved this plan and `tasks.md` on 2026-09-23. Approval authorizes task-scoped implementation; it is not evidence that migration, code, tests, or deployment have run.
