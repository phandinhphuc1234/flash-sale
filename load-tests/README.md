# Load and Capacity Tests

Load tests are controlled experiments, not an authorization to saturate infrastructure. They record
the maximum safe point for a specific commit, topology, dataset, route, duration, and guardrail set.

## Test assets

| Area | Files | Purpose |
|---|---|---|
| Flash Sale | `flashsale-service/reservation.js` | fixed concurrent reservation scenario |
| Flash Sale | `flashsale-service/adaptive-arrival-rate.js` | constant-arrival-rate RPS stage |
| Flash Sale | `flashsale-service/run-ladder.ps1` | fixture/token setup and bounded ladder orchestration |
| Flash Sale → Order | [`flash-sale-to-order-stress/README.md`](flash-sale-to-order-stress/README.md) | new purchases, same-key replay and sold-out contention with owner Order correlation |
| Flash Sale reservation burst | [`flash-sale-reservation-burst/README.md`](flash-sale-reservation-burst/README.md) | one-second hot-SKU reservation burst; Redis/Kafka boundary only |
| Local platform | [`full-stack-smoke/README.md`](full-stack-smoke/README.md) | starts Compose and runs a no-token k6 readiness smoke across all services |
| Order | `order-service/order-query.js` | owner Order query behavior |
| Payment | `payment-service/feature-021-payment.js` | payment feature load checks |

Generated results and real shopper tokens must remain untracked. Use
`shopper-tokens.example.json` only as a shape example.

## RPS versus concurrency

- **RPS** is the arrival rate: requests started per second.
- **Concurrency/VUs** is simultaneous in-flight work. Slow responses can raise concurrency even at
  the same RPS.
- A one-shot burst of 1,000 users is not automatically “1,000 RPS”.

Measure accepted outcomes, duplicates/replays, sold-out responses, HTTP errors, dropped iterations,
and p95/p99 latency together. A low error rate with multi-second p99 latency is still a failed user
experience.

## Guarded execution

1. Verify the functional/idempotency scenario first.
2. Seed a known allocation and unique shopper tokens.
3. Warm up at low traffic.
4. Increase stages gradually and stop on the first warning/danger guard.
5. Confirm no oversell and inspect Redis Stream, PostgreSQL journal/outbox, Kafka lag, and service
   health after the run.
6. Store a sanitized result with commit/environment/profile; remove token files.

Example fixture-driven ladder:

```powershell
pwsh -NoLogo -NoProfile -File .\load-tests\flashsale-service\run-ladder.ps1 `
  -CampaignId <campaign-guid> `
  -VariantId <variant-guid> `
  -LevelsCsv "50,100,200" `
  -ExpectedAllocation 500
```

Do not copy 5,000–10,000 RPS targets from a distributed design into a single workstation or a
three-node internship cluster. First prove the lower stage and capacity bottleneck, then design
distributed generators and an explicit cloud budget.

The dated current evidence is in
[`specs/046-seckill-capacity-gate/validation.md`](../specs/046-seckill-capacity-gate/validation.md).
