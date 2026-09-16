# Flash Sale reservation burst

This profile tests the hot-path boundary only:

```text
shopper JWT → API Gateway → Flash Sale reservation → Redis atomic stock → Kafka event
```

It intentionally does not call Order or Payment. The initial profile is **5,000 arrival
attempts per second for one second**. With one request per iteration, that is 5,000 reservation
requests, not 5,000 users maintained for one second. Use a disposable campaign/variant whose
allocation is known (for example, 2,000 units) and provide at least one distinct, unexpired JWT
per planned arrival.

## What is measured

- `202` durable reservation winners;
- `409 FLASH_SALE_SOLD_OUT` expected losers;
- `503 FLASH_SALE_ACCEPTANCE_PENDING` reported as a capacity warning;
- unexpected HTTP responses, transport errors, dropped iterations;
- p95/p99 reservation latency;
- accepted reservations compared with the declared allocation.

The runner fails on dropped iterations, transport/unexpected errors, acceptance-pending responses,
or accepted reservations above the declared allocation. It cannot prove no oversell by itself;
inspect the fixture's authoritative stock/reservation state after the run.

## Prepare and run

The token file is local-only and must stay Git-ignored:

```json
["<shopper-jwt-1>", "<shopper-jwt-2>"]
```

Validate without sending traffic:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-flash-sale-reservation-burst.ps1 `
  -CampaignId <campaign-guid> `
  -VariantId <variant-guid> `
  -ExpectedAllocation 2000
```

Run the explicit one-second burst:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-flash-sale-reservation-burst.ps1 `
  -CampaignId <campaign-guid> `
  -VariantId <variant-guid> `
  -ExpectedAllocation 2000 `
  -Rate 5000 `
  -DurationSeconds 1 `
  -VirtualUsers 5000 `
  -MaxVirtualUsers 6000 `
  -AllowHighBurst `
  -Run
```

This is intentionally guarded for a local workstation. A failure can indicate generator/VU
saturation rather than Flash Sale capacity; record the report and inspect Docker CPU, Redis,
Kafka, and service health before rerunning. Do not infer a production SLA from this result.
