# Quickstart: Phase 26 Adaptive Seckill Capacity Gate

## 1. Validate without running load

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase26-seckill-capacity.ps1 `
  -CampaignId <CAMPAIGN_UUID> `
  -VariantId <VARIANT_UUID>
```

This parses the k6 profile and validates the bounded parameters. It does not start k6 or change
cluster state.

## 2. Run locally first

Use a disposable campaign/variant with allocation above the planned request count and a local
Gateway URL. Before `-Run`, provide an ignored JSON array of disposable shopper access tokens at
`load-tests/flashsale-service/shopper-tokens.json` (copy the shape of
`shopper-tokens.example.json`). The runner requires one distinct token per arrival across the full
ladder plus one second of scheduling headroom per stage; it does not create users or print tokens.
For a short rehearsal, reduce the stage duration and cap so the token requirement stays small. It
writes a sanitized report under the ignored results directory:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase26-seckill-capacity.ps1 `
  -CampaignId <CAMPAIGN_UUID> `
  -VariantId <VARIANT_UUID> `
  -Run `
  -GatewayBaseUrl http://127.0.0.1:18080 `
  -StartRate 25 -StepRate 25 -MaxRate 100 `
  -WarmupDurationSeconds 5 -StageDurationSeconds 10 -CooldownSeconds 5
```

## 3. Controlled cloud run

Only after Phase 21/22/24/25 health gates pass, use the HTTPS Gateway and a low cap. Do not start
with 500 RPS:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase26-seckill-capacity.ps1 `
  -CampaignId <CAMPAIGN_UUID> `
  -VariantId <VARIANT_UUID> `
  -Run `
  -GatewayBaseUrl https://api.flashsale123.tech `
  -StartRate 25 -StepRate 25 -MaxRate 250
```

## 4. Interpret the report

- `stopped_on_breach`: `lastGoodRate` is the highest rate meeting the configured guardrails;
  `firstBreachRate` is the next stage that crossed them.
- `at_or_above_max_tested`: every tested stage passed; this is a lower bound, not true capacity.
- Verify winners never exceed allocation, replay IDs remain stable, and Kafka lag settles.

The runner does not replace the existing `run-ladder.ps1`; use that profile for burst/concurrency
comparison when desired.
