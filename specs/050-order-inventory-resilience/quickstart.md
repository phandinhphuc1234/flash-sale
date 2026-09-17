# Quickstart: Order to Inventory Resilience

This is the operator quickstart for the implemented Feature 050 slice. It validates the Order-to-
Inventory resilience boundary locally without cloud credentials or production data. A passing local
gate does not imply that a cloud rollout has been performed.

## 1. Confirm feature context

```powershell
Get-Content .specify\feature.json
git branch --show-current
```

Expected feature directory: `specs/050-order-inventory-resilience`.

## 2. Review and approve before implementation

Review, in order:

1. [spec.md](spec.md) — approved behavior and non-goals.
2. [research.md](research.md) — why the explicit decorator/bulkhead/breaker design was selected.
3. [plan.md](plan.md) — implementation structure, defaults, and gates.
4. [contracts/order-inventory-resilience-policy.md](contracts/order-inventory-resilience-policy.md)
   — unchanged HTTP boundary plus failure/admission semantics.

The project owner approved the plan on 2026-09-16 and the Spec Kit task-generation workflow produced
`tasks.md`. Production work must follow one unchecked task or one coherent task group from that file.

## 3. Implemented validation checks

```powershell
.\mvnw.cmd -pl services/order-service -am dependency:tree `
  "-Dincludes=io.github.resilience4j"

pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-050-order-inventory-resilience.ps1 `
  -Scenario All -TimeoutSeconds 900

.\mvnw.cmd -pl services/order-service -am verify
```

The dependency check resolves Resilience4j `2.2.0` through the service-local Spring Boot 3
starter. The Feature 050 runner is the fast repeatable gate and prints one marker per scenario;
the module verify is the slower regression gate because Testcontainers creates isolated PostgreSQL
instances for the Order integration contexts. As captured in `validation.md`, both gates pass.

## 4. Required fault drill

The deterministic fixture must demonstrate all of these without external cloud infrastructure:

1. Infrastructure failures cross the configured evidence window and open the breaker.
2. One hundred subsequent attempts fail safely and at least 95% complete within 100 ms.
3. Those open-state attempts make zero Inventory delegate calls.
4. One hundred business rejections do not open the breaker.
5. The admitted delegate concurrency never exceeds the configured bulkhead limit.
6. Successful bounded probes close the breaker after Inventory recovery.
7. Recovery reuses the original business identities and creates no duplicate durable effect.
8. Order readiness remains healthy while dependency-isolation telemetry reports the outage.

## 5. Rollback rehearsal and cloud boundary

Restore the previous Order image/configuration and rerun the Feature 049 regression suite. Because
this feature changes no data or wire contract, rollback must require no migration, topic/schema
change, or Inventory deployment. The local rehearsal is represented by the runner's identity and
boundary checks; no cloud image rollback was executed in this validation because the EKS environment
is not part of this feature gate. Treat cloud rollback as deferred operational work, not as a
passed local test.
