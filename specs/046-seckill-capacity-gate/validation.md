# Validation Evidence: Adaptive Seckill Capacity Gate

## Status

Static implementation and a disposable local Gateway rehearsal are complete on
`codex/phase26-seckill-capacity`. A larger local ladder and any cloud run remain explicit operator
actions with fresh disposable allocations and enough unique shopper tokens.

## Planned gates

| Gate | Command | Result |
|---|---|---|
| PowerShell static safety | `pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\tests\phase26-seckill-capacity.tests.ps1` | PASS |
| Validation-only runner | `pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase26-seckill-capacity.ps1 -CampaignId <UUID> -VariantId <UUID> -StartRate 25 -StepRate 25 -MaxRate 100 -WarmupDurationSeconds 5 -StageDurationSeconds 10 -CooldownSeconds 5` | PASS; k6 not started |
| k6 profile inspection | `k6 inspect --no-thresholds -e FLASHSALE_CAMPAIGN_ID=<UUID> -e FLASHSALE_VARIANT_ID=<UUID> -e FLASHSALE_SHOPPER_TOKENS_FILE=<ignored-file> .\load-tests\flashsale-service\adaptive-arrival-rate.js` | PASS; constant-arrival-rate parsed |
| Local adaptive run | `phase26-seckill-capacity.ps1 -CampaignId 44615210-fce3-4f2d-bfdb-146f794fb5d8 -VariantId 89591f6d-d04a-449f-a069-d4f243fd3671 -ExpectedAllocation 100 -WarmupRate 1 -WarmupDurationSeconds 1 -StartRate 1 -StepRate 1 -MaxRate 1 -StageDurationSeconds 5 -CooldownSeconds 0 -Run` | PASS; 5 winners and 5 identity-preserving replays, p95 163.778 ms, p99 175.059 ms, zero HTTP/unexpected errors, zero dropped iterations, outcome `at_or_above_max_tested` |
| Cloud bounded run | See `quickstart.md` | Pending explicit operator run |

The sanitized local report was written as
`load-tests/flashsale-service/results/adaptive-20260828T200414Z-b78a6873.json`; the results directory
and shopper-token file remain Git-ignored. The rehearsal also exposed and fixed two harness defects
before any larger test: the k6 threshold now references its built-in `dropped_iterations` metric,
and the PowerShell JSON parser no longer aliases its case-insensitive typed `Property` parameter.

## Progressive local ladder

After the 1 RPS rehearsal, a fresh allocation-700 fixture was exercised through the local Gateway
with a 2 RPS warm-up and 10/20/30 RPS stages of 10 seconds each. The runner reserved 672 unique
shopper tokens, including one second of scheduling headroom per stage.

| Rate | HTTP requests | Winners | Replays | p95 | p99 | HTTP/unexpected errors | Dropped |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 RPS | 202 | 101 | 101 | 79.346 ms | 298.017 ms | 0 | 0 |
| 20 RPS | 402 | 201 | 201 | 44.922 ms | 68.048 ms | 0 | 0 |
| 30 RPS | 602 | 301 | 301 | 33.267 ms | 63.919 ms | 0 | 0 |

Result: `at_or_above_max_tested`, `lastGoodRate=30`, no breach or danger reason. Gateway readiness
remained HTTP 200 and PostgreSQL, Redis, Kafka, and Schema Registry remained healthy. The sanitized
report is `load-tests/flashsale-service/results/adaptive-20260828T205458Z-6adac52a.json`.

An earlier 10 RPS attempt was rejected as capacity evidence because `gracefulStop: 0s` could abort
the final replay and exact `rate * duration` token offsets could overlap when k6 scheduled a boundary
iteration. The fixed harness uses a bounded 30-second graceful stop, non-overlapping token budgets,
parses k6 threshold exit code 99, and returns non-zero for `stopped_on_danger` as required by SC-002.
