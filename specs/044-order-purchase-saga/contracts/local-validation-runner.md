# Operator Contract: Feature 044 Local Validation Runner

## Command surface

Planned script:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 `
  -Scenario <Contracts|Start|Paid|Failed|Replay|LateSuccess|All>
```

Optional switches may include topology reuse, preserved sanitized fixture output, and an explicit
Stripe CLI/test-mode scenario. Defaults must be safe, bounded, and local.

## Scenario contract

| Scenario | Required evidence |
|---|---|
| `Contracts` | New topic definitions, eight Avro records, generated SpecificRecords, schema tests, and Registry compatibility pass. |
| `Start` | One accepted purchase creates one Order/Saga and publishes one semantic Payment request; duplicate acceptance changes nothing. |
| `Paid` | Payment success produces confirm command, confirmed reservation, confirmed Order, and one `OrderConfirmedV1`. |
| `Failed` | Each approved Payment failure produces one release command/result and the approved cancelled/expired Order event. |
| `Replay` | Duplicate commands/results and process restart preserve one semantic effect and stable IDs. |
| `LateSuccess` | Higher-version paid success supersedes failure, attempts forward confirmation, and converges to confirmed or to durable manual review plus one `OrderPaymentReviewRequiredV1` correction fact without refund. |
| `All` | Runs all deterministic scenarios, affected Maven module verification, full reactor verification, and sanitized summary. |

## Boundary rules

- The runner may call service HTTP APIs, invoke existing service-owned fixture jobs/tests, publish
  generated Avro records through test adapters, and query sanitized public/actuator evidence.
- It must not insert/update another service's tables, fabricate outbox rows, bypass Stripe signature
  verification in a provider scenario, or print `.env` values.
- Direct SQL may be used only by a service-owned integration test inside that service module or for
  explicitly documented operator evidence after business behavior was exercised through the owning
  boundary. The cross-service runner does not use SQL as a fixture mechanism.
- Local secrets remain in ignored `infra/docker/.env`; the script reports missing variable names
  only.
- Passwords are requested with `Read-Host -AsSecureString` or supplied through a memory-only wrapper;
  they are never written to Git or output.
- Temporary fixture files are created outside tracked paths, contain no tokens, and are removed by
  default.

## Bounded execution

- Every native process and poll has a timeout and includes the failing stage in its error.
- Topology readiness and each business transition use finite polling.
- Failure output is truncated to a safe diagnostic tail.
- The aggregate runner terminates child port-forward/CLI processes in `finally` blocks.
- Re-running after partial failure uses new business fixture identities and does not require database
  deletion.

## PASS summary

The final output contains identifiers safe for troubleshooting and these stage labels:

```text
FEATURE_044_CONTRACTS=PASS
FEATURE_044_START=PASS
FEATURE_044_PAID=PASS
FEATURE_044_FAILED=PASS
FEATURE_044_REPLAY=PASS
FEATURE_044_LATE_SUCCESS=PASS
FEATURE_044_MODULES=PASS
FEATURE_044_MONOREPO=PASS
FEATURE_044_LOCAL_GATE=PASS
```

It never prints JWTs, passwords, Stripe keys, webhook signatures, raw webhook bodies, Checkout URLs,
or Authorization headers.

## Cloud handoff

`FEATURE_044_LOCAL_GATE=PASS` is required before:

1. provisioning the new EKS topic/subjects;
2. running the selective eight-service delivery workflow for affected modules;
3. merging the immutable image-promotion PR;
4. waiting for Argo `Synced/Healthy` and affected Deployment rollout;
5. running Phase 21 verification and the extended Phase 24 Stripe cloud runner.

The local runner never applies Kubernetes resources or pushes images.
