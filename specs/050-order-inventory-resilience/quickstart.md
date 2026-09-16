# Quickstart: Order to Inventory Resilience

This is a planning artifact. The commands below describe the expected implementation validation;
they do not mean production code exists yet.

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

After the project owner approves the plan, run the Spec Kit task-generation workflow. Do not change
production code before that approval and an approved `tasks.md`.

## 3. Planned implementation checks

```powershell
.\mvnw.cmd -pl services/order-service -am dependency:tree `
  -Dincludes=io.github.resilience4j

.\mvnw.cmd -pl services/order-service -am verify
```

The implementation tasks must add focused commands for the named resilience configuration,
fault-state, concurrency, and Feature 049 recovery tests.

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

## 5. Rollback rehearsal

Restore the previous Order image/configuration and rerun the Feature 049 regression suite. Because
this feature changes no data or wire contract, rollback must require no migration, topic/schema
change, or Inventory deployment.
