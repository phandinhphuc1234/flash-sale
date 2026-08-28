# Data Model: Adaptive Seckill Capacity Gate

The feature stores only ignored, sanitized test evidence. It does not add a business table or alter
service-owned state.

| Record | Fields | Meaning |
|---|---|---|
| TestRunReport | runId, route, campaignId, variantId, generatedAt, thresholds, stages, outcome, cleanup | Complete operator result; IDs are fixture identifiers, not secrets |
| StageResult | rateRps, durationSeconds, requests, winners, replays, soldOut, acceptancePending, unexpectedErrors, droppedIterations, p95Ms, p99Ms, httpErrorRate, expectedOutcomeRate, breachReasons, dangerReasons | One bounded k6 stage; platform health is a Phase 25 prerequisite, not fabricated by this runner |
| StopDecision | outcome, lastGoodRate, firstBreachRate, stopReason | Why the runner stopped |

Token files are transient credentials and are never part of the report. They are ignored by Git and
removed in `finally` where possible.
