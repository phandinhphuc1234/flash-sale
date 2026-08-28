# Validation Evidence: Adaptive Seckill Capacity Gate

## Status

Static implementation complete on `codex/phase26-seckill-capacity`; live load remains an explicit
operator action because it requires disposable campaign data and shopper tokens.

## Planned gates

| Gate | Command | Result |
|---|---|---|
| PowerShell static safety | `pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\tests\phase26-seckill-capacity.tests.ps1` | PASS |
| Validation-only runner | `pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase26-seckill-capacity.ps1 -CampaignId <UUID> -VariantId <UUID> -StartRate 25 -StepRate 25 -MaxRate 100 -WarmupDurationSeconds 5 -StageDurationSeconds 10 -CooldownSeconds 5` | PASS; k6 not started |
| k6 profile inspection | `k6 inspect --no-thresholds -e FLASHSALE_CAMPAIGN_ID=<UUID> -e FLASHSALE_VARIANT_ID=<UUID> -e FLASHSALE_SHOPPER_TOKENS_FILE=<ignored-file> .\load-tests\flashsale-service\adaptive-arrival-rate.js` | PASS; constant-arrival-rate parsed |
| Local adaptive run | See `quickstart.md` | Pending operator fixture |
| Cloud bounded run | See `quickstart.md` | Pending explicit operator run |

No live load was executed while this feature was implemented.
