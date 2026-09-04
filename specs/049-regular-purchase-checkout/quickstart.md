# Quickstart: Feature 049 Regular Purchase Checkout

This is the planned implementation/validation sequence. Commands referring to Feature 049 scripts
become runnable only after the corresponding task group implements them. This document does not
authorize production code or cloud mutation before plan/tasks approval and local PASS.

## 1. Verify branch and active feature

```powershell
git status --short --branch
Get-Content .specify\feature.json
```

Expected feature pointer:

```json
{"feature_directory":"specs/049-regular-purchase-checkout"}
```

Preserve unrelated working-tree files. Do not stage `.env`, JWT keys, Stripe keys, OAuth client
secrets, or temporary extracts.

## 2. Approval boundary

Current state after `/speckit-plan`:

```text
spec.md       approved
plan.md       approved
research.md   complete
data-model.md complete
contracts/    complete planning contracts
tasks.md      approved for implementation on 2026-09-03
```

Production code may now proceed phase by phase from the approved task ledger.

## 3. Planned local prerequisites

- Java 21 and Maven Wrapper
- Docker Desktop with Linux containers
- PowerShell 7 (`pwsh`)
- local PostgreSQL, Kafka, Schema Registry, Authentication, Gateway, Product, Cart, Inventory,
  Order, and Payment services
- ignored `infra/docker/.env` containing existing platform secrets plus the new Order machine-client
  secret at both Authentication provisioning and Order client boundaries
- Stripe test values only for the optional final real-provider scenario

Secret validation scripts may verify key names/presence but must never print values.

## 4. Planned focused gates

### G1 — Contracts

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl contracts/kafka-avro-contracts -am verify

pwsh -NoLogo -NoProfile -File `
  .\infra\docker\smoke\feature-049-regular-purchase.ps1 `
  -Scenario Contracts
```

Expected label: `FEATURE_049_CONTRACTS=PASS`.

### G2 — Cart snapshot/reconciliation

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/cart-service -am verify
```

Required evidence:

- existing Cart CRUD still behaves identically except additive version fields;
- wrong internal subject/scope is rejected;
- exact snapshot returns Cart/item revisions;
- confirmed cleanup removes unchanged rows;
- quantity edit and remove/re-add preserve later intent;
- 100 command replays have one semantic effect.

### G3 — Product quote and Authentication client

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/product-service,services/authentication-service -am verify
```

Verify current authoritative price/sellability, exact Order subject/scope, and unchanged existing
Cart/Campaign internal contracts.

### G4 — Inventory regular holds

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/inventory-service -am verify
```

Required evidence includes fresh/upgrade migration, all-or-nothing multi-line hold, five-minute
expiry, deterministic row-lock order, no oversell, confirm/release replay, command conflict, outbox,
DLT, and campaign-allocation regression.

### G5/G6 — Order intake and generalized Saga

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/order-service -am verify
```

Required evidence includes both public endpoints, owner derivation, idempotency conflict/replay,
Cart/price/stock rejection, multi-line total, no transaction across HTTP, PaymentRequestedV1 reuse,
regular participant finalization, late success/manual review, and unchanged Flash Sale branch.

## 5. Aggregate local gate

Run focused scenarios before `All`:

```powershell
$scenarios = @(
  'BuyNowPaid',
  'CartPaid',
  'CartEditedWhilePaying',
  'PriceChanged',
  'InsufficientStock',
  'PaymentFailed',
  'HoldExpired',
  'Replay',
  'Concurrency',
  'FlashSaleRegression'
)

foreach ($scenario in $scenarios) {
  pwsh -NoLogo -NoProfile -File `
    .\infra\docker\smoke\feature-049-regular-purchase.ps1 `
    -Scenario $scenario
  if ($LASTEXITCODE -ne 0) { throw "Feature 049 failed: $scenario" }
}

pwsh -NoLogo -NoProfile -File `
  .\infra\docker\smoke\feature-049-regular-purchase.ps1 `
  -Scenario All
```

Then run repository gates:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/authentication-service,services/product-service,services/cart-service,services/inventory-service,services/order-service,services/payment-service -am verify

.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

kubectl apply --dry-run=client -k infra/k8s/overlays/cloud

git diff --check
```

Expected aggregate label: `FEATURE_049_LOCAL_GATE=PASS`. A long-running full reactor needs its own
execution budget; a script timeout is not a successful Maven exit.

## 6. Planned cloud contract and migration gate

Cloud mutation is blocked until the local aggregate gate and CI are green. After merging the source
PR and pulling reviewed `develop`:

```powershell
git switch develop
git pull --ff-only origin develop

pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase20-kafka-contracts.ps1

pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply
```

The provisioning extension must be additive/idempotent and verify all new source and DLT subjects.
If its existing Payment safety gate requires Payment flags disabled, pause them through a reviewed
GitOps change; never bypass the gate.

After the delivery workflow pushes all affected `release-<develop SHA>` images and before merging
the image-promotion PR, run the future migration gate:

```powershell
pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase49-regular-purchase-migration-gate.ps1

pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase49-regular-purchase-migration-gate.ps1 -Apply
```

It must run service-owned migration Jobs sequentially:

1. Cart expansion;
2. Inventory regular holds/outbox;
3. Order regular intake/generalization.

It never reads/prints Secret values and never deletes/reruns a completed Job.

## 7. Planned one-image-promotion release

Use **Eight-Service GitOps Delivery** once for the final reviewed source SHA. Select exactly the
affected services reported by the source diff; expected image targets are Cart, Product, Inventory,
Order, and Authentication when provisioning code/config is image-bound. Payment is not rebuilt
unless its production inputs changed.

Workflow flow:

```text
source PR -> develop -> module verification -> immutable ECR images
          -> one image-promotion PR -> migration gate PASS -> merge promotion
          -> Argo reconciliation -> Deployment rollout -> disabled-state verification
```

After merge:

```powershell
kubectl -n argocd annotate application flash-sale-cloud `
  argocd.argoproj.io/refresh=hard --overwrite

kubectl -n argocd get application flash-sale-cloud --watch

.\infra\scripts\gitops\phase21-cloud-release-verify.ps1 `
  -PaymentRuntimeState enabled
```

Verify actual pod image digests, not only ECR tag existence.

## 8. Enable and verify regular purchase

Enable regular intake through a small reviewed GitOps config PR only after migrations, topics,
Registry subjects, consumers, secrets, Argo, and Deployments are healthy.

The future cloud runner must test through HTTPS Gateway:

```powershell
pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase49-regular-purchase-cloud.ps1 `
  -Run `
  -AdminLogin "admin@flashsale.test"
```

Required PASS evidence:

- Buy Now accepted -> one Payment -> Stripe success -> Inventory confirmed -> Order confirmed;
- multi-line Cart accepted -> one Payment -> confirmed -> only unchanged snapshot entries removed;
- price mismatch and insufficient stock leave no Order/Payment/lasting hold;
- unpaid/expiry releases hold and leaves Cart unchanged;
- equivalent replay and duplicate events have one semantic effect;
- Flash Sale Phase 22/24 regression remains green;
- no Checkout URL, Authorization header, password, webhook secret/body, or customer data is printed.

## 9. Rollback rehearsal

Rollback order:

1. disable regular purchase intake through reviewed GitOps;
2. preserve/drain or explicitly record accepted intake, Saga, hold, inbox, and outbox work;
3. run compatibility rehearsal against exact prior immutable image tags;
4. restore only images that understand every persisted schema/enum value;
5. verify Argo, existing Flash Sale purchase, Payment, Cart CRUD, and no data/topic deletion.

Do not reverse Liquibase changes. Once regular rows exist, an old image that only understands Flash
Sale rows is not a valid rollback target; keep data and ship a compatible forward fix.
