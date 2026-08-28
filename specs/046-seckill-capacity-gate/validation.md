# Validation Evidence: Adaptive Seckill Capacity Gate

## Status

The replay-safe runner was merged to `develop` in PR #126 (`8e66a58`). A controlled local Gateway
ladder has now been exercised through 200 RPS with fresh disposable campaigns, fresh shopper tokens,
and the Order consumer recovered to zero lag. The 200 RPS stage breached the latency guardrail, so
300/500/750/1,000 RPS and any cloud run were intentionally not started.

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

## Controlled local ladder through 200 RPS

The following single-stage runs used the merged runner, a 5 RPS / 3 second warm-up, a 5 second
stage, 2 second cooldown, `ConsecutiveBreaches=1`, and pre-allocated VUs (200 through 50 RPS, 400
at 100 RPS, and 800 at 200 RPS). Each run used a disposable campaign/variant and a fresh token
batch; values below are sanitized report metrics. Each arrival performs one winner request and one
same-key replay, so the HTTP request count is approximately twice the arrival count.

| Rate | HTTP requests | Winners | Replays | p95 | p99 | HTTP/unexpected errors | Dropped | Outcome |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 30 RPS | 300 | 150 | 150 | 36.374 ms | 57.533 ms | 0 | 0 | PASS |
| 32 RPS | 322 | 161 | 161 | 293.853 ms | 642.129 ms | 0 | 0 | PASS |
| 33 RPS | 332 | 166 | 166 | 165.094 ms | 374.073 ms | 0 | 0 | PASS |
| 34 RPS | 342 | 171 | 171 | 43.373 ms | 79.944 ms | 0 | 0 | PASS |
| 35 RPS | 352 | 176 | 176 | 46.956 ms | 150.118 ms | 0 | 0 | PASS |
| 40 RPS | 400 | 200 | 200 | 38.973 ms | 83.055 ms | 0 | 0 | PASS |
| 45 RPS | 452 | 226 | 226 | 27.627 ms | 54.709 ms | 0 | 0 | PASS |
| 50 RPS | 502 | 251 | 251 | 298.907 ms | 447.478 ms | 0 | 0 | PASS |
| 100 RPS | 1,000 | 500 | 500 | 54.563 ms | 101.426 ms | 0 | 0 | PASS |
| 200 RPS | 2,002 | 1,001 | 1,001 | 397.220 ms | 594.515 ms | 0 | 0 | STOP: p95 breach |

The 200 RPS stage is the first official guardrail breach (`p95LimitMs=300`); both winner and replay
requests contributed to the p95 (424.946 ms and 354.840 ms respectively), while p99 remained below
700 ms and correctness remained intact. Therefore the aggregate ladder result is
`lastGoodRate=100 RPS` and `firstBreachRate=200 RPS` for this short local profile—not a claim that
the service's absolute capacity is 100 RPS. The 50/100/200 values are separate controlled runs, so
the non-monotonic p95 values are a reason to repeat longer stages before making a capacity claim.
No 300/500/750/1,000 RPS stage was run after the breach.

Two earlier exploratory attempts (50 RPS with 17 dropped iterations and 35 RPS with an Order Kafka
backlog) are excluded from the table: the first exposed insufficient VU pre-allocation, and the
second ran while the local consumer group was replaying old records after a missing DLT schema.
After registering the approved Order DLT schemas and waiting for `order-purchase-accepted-v1` lag to
reach zero, the controlled runs above had zero dropped iterations and zero unexpected errors.

Representative sanitized reports:

- 50 RPS: `adaptive-20260828T213321Z-784c1741.json`
- 100 RPS: `adaptive-20260828T213424Z-47c51139.json`
- 200 RPS: `adaptive-20260828T213542Z-ef957ea6.json`

Platform recovery checks after the ladder: Gateway readiness 200, Flash Sale readiness 200,
Schema Registry `/subjects` 200, and Order consumer lag 0. The token file and all JSON reports stay
Git-ignored; no token, password, Secret value, or Authorization header was recorded.
