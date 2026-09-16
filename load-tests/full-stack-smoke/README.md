# Full-stack local readiness smoke

`run-full-stack-readiness-smoke.ps1` is the safe first check before authenticated load tests.
It starts the local Compose backing services and ten Spring services, waits until every
`/actuator/health/readiness` endpoint reports `HTTP 200` with `status=UP`, then runs the k6 profile
`full-stack-readiness.js` at a bounded constant arrival rate.

This is not a business-flow, seckill, or capacity benchmark. It does not need JWTs, campaign
fixtures, Stripe, EKS, database queries, or Kubernetes. The authenticated seckill runner remains
`infra/scripts/load/run-flash-sale-to-order-stress.ps1` and requires fresh shopper tokens plus a
dedicated campaign allocation.

## Run

From the repository root:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-full-stack-readiness-smoke.ps1 `
  -Run -Rate 1 -DurationSeconds 20
```

Use `-Build` only when local images need rebuilding. The runner never executes `docker compose down`
and never removes volumes. A sanitized report is written under the ignored directory
`load-tests/full-stack-smoke/results/<UTC-run>/report.json`.

The report tells you whether all services stayed ready, the number of iterations and checks, HTTP
failure rate, and readiness latency. A passing report proves the local platform is alive at the
tested rate; it does not prove order creation, Kafka delivery, payment, or maximum capacity.
