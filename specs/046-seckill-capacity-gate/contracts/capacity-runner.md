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
