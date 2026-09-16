# Capacity Runner Contract

## Invocation

```text
phase26-seckill-capacity.ps1
  -CampaignId <UUID>
  -VariantId <UUID>
  [-Run]
  [-Route gateway|direct-service]
  [-StartRate 25] [-StepRate 25] [-MaxRate 500]
  [-StageDurationSeconds 30] [-CooldownSeconds 20]
  [-P95LimitMs 300] [-P99LimitMs 700] [-ErrorRateLimit 0.01]
  [-ConsecutiveBreaches 2] [-TimeoutSeconds 900]
  [-ShopperTokenFile load-tests/flashsale-service/shopper-tokens.json]
```

Validation-only mode must not start k6 or mutate Kubernetes, databases, Kafka, Redis, ECR, or
Secrets. `-Run` is the explicit opt-in for a disposable fixture load.

## Result contract

The runner writes a Git-ignored JSON report with `schemaVersion`, `stages`, `lastGoodRate`,
`firstBreachRate`, `outcome`, `stopReason`, and `cleanup`. Values for passwords, tokens, JWTs,
Authorization headers, Secret data, and Checkout URLs are forbidden.

`outcome` is one of:

- `stopped_on_breach`
- `stopped_on_danger`
- `at_or_above_max_tested`
- `runner_failure`

## Business result contract

Only `202` is counted as a durable winner. `409 FLASH_SALE_SOLD_OUT` and `503
FLASH_SALE_ACCEPTANCE_PENDING` are expected classifications. All other responses are unexpected and
fail the stage.

## Independent Flash Sale to Order runner (FR-011–FR-014)

`infra/scripts/load/run-flash-sale-to-order-stress.ps1` accepts `-CampaignId`, `-VariantId`,
`-ExpectedAllocation`, optional `-Scenario NewOrders|Replay|SoldOut`, `-RatesCsv`,
`-ShopperTokenFile`, `-GatewayBaseUrl`, `-Run`, and `-AllowRemote`. Default is offline validation.
The complete option documentation is in `load-tests/flash-sale-to-order-stress/README.md`.

- POST `/api/v1/flash-sales/{campaignId}/reservations`: JWT + Idempotency-Key; quantity 1.
- GET `/api/v1/orders?page=0&size=2`: `data.data` items, `data.page` metadata. Baseline must be empty.
- GET `/api/v1/orders/{id}`: owner-scoped correlation of all four identities and quantity.
- First and same-key replay responses must be 202 with matching accepted payloads. Only a first
  accepted POST increments accepted-unique count. Only a correlated detail increments observed Orders.
- Only `409 FLASH_SALE_SOLD_OUT` is an expected rejection, and only in SoldOut mode.
- `503 FLASH_SALE_ACCEPTANCE_PENDING`, network failures, malformed envelopes, unexpected states
  and incomplete observations are non-passing outcomes, never counted as confirmed lost purchases.
- The report has a fixed allowlist of aggregate numeric metrics and failure classifications. It
  excludes tokens, raw bodies/headers, checkout URLs and per-user identifiers. Counts are scoped to
  this run; allocation comparisons assume a fresh exclusive fixture.
- Observer visibility is not database commit timing; stage tail is not measured Kafka lag. No broker
  or DB evidence is fabricated. Cancellation may leave real reservations and Orders requiring review.
