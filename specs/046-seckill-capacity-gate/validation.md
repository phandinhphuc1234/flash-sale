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
