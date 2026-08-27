# Quickstart: Feature 044 Purchase Saga

This guide describes the planned validation sequence. Commands that reference Feature 044 assets
become runnable as their corresponding task groups are implemented. It does not authorize cloud
deployment before the local aggregate gate passes.

## 1. Verify branch and feature selection

```powershell
git status --short --branch
Get-Content .specify\feature.json
```

Expected:

```text
codex/order-purchase-saga
{"feature_directory":"specs/044-order-purchase-saga"}
```

Do not discard unrelated working-tree changes.

## 2. Local prerequisites

Required tools:

- Java 21
- Maven Wrapper
- Docker Desktop with Linux containers
- PowerShell 7 (`pwsh`)
- enough local resources for PostgreSQL, Redis, Kafka, Schema Registry, Gateway, Auth, Product,
  Inventory, Campaign, Flash Sale, Order, and Payment

Use ignored `infra/docker/.env`. The runner validates names/formats without printing values. Existing
local system values remain required, including PostgreSQL/Redis/JWT/service-client configuration.
Stripe test values are required only for the optional real-provider/local-CLI scenario:

```text
STRIPE_SECRET_KEY
STRIPE_PUBLISHABLE_KEY
STRIPE_WEBHOOK_SECRET
```

Never paste these values into Git, Markdown, shell history, or test output.

## 3. Validate one implementation group locally

### G1 contracts

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl contracts/kafka-avro-contracts -am verify

pwsh -NoLogo -NoProfile -File `
  .\infra\docker\smoke\feature-044-purchase-saga.ps1 `
  -Scenario Contracts
```

Expected final label:

```text
FEATURE_044_CONTRACTS=PASS
```

### G2 Saga start and Payment request

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/order-service -am verify

pwsh -NoLogo -NoProfile -File `
  .\infra\docker\smoke\feature-044-purchase-saga.ps1 `
  -Scenario Start
```

Expected behavior:

- duplicate `PurchaseAcceptedV1` resolves to one Order and one Saga;
- Saga state is `PAYMENT_PENDING`;
- Payment deadline is exactly reservation expiry minus 30 seconds;
- one semantic `PaymentRequestedV1` reaches Payment;
- message key is `orderId` and identities/traces remain stable.

### G3 Payment-result handling

Run the Order module gate again after Payment result consumers are implemented. Focused integration
tests must cover valid success/failure, malformed input, identity conflict, stale version,
higher-version success, retry, and DLT behavior.

### G4 Flash Sale finalization

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/flashsale-service -am verify
```

Required evidence:

- confirm and release domain transitions;
- command inbox and outcome outbox atomicity;
- same-command and competing-command behavior;
- confirmed reservation never expires/restores quota;
- release restores Redis stock/user quota once;
- Redis outage leaves a durable reconciliation backlog and recovery clears it.

### G5 paid, failed, replay, and late success

```powershell
$scenarios = @('Paid', 'Failed', 'Replay', 'LateSuccess')
foreach ($scenario in $scenarios) {
  pwsh -NoLogo -NoProfile -File `
    .\infra\docker\smoke\feature-044-purchase-saga.ps1 `
    -Scenario $scenario
  if ($LASTEXITCODE -ne 0) { throw "Feature 044 scenario failed: $scenario" }
}
```

Expected terminal rules:

| Input | Reservation | Order | Saga |
|---|---|---|---|
| Payment succeeded | `CONFIRMED` | `CONFIRMED` | `COMPLETED` |
| Payment deadline expired | `RELEASED` or already `EXPIRED` | `EXPIRED` | `COMPENSATED` |
| Checkout attempt limit | `RELEASED` or already `EXPIRED` | `CANCELLED` | `COMPENSATED` |
| Provider terminal failure | `RELEASED` or already `EXPIRED` | `CANCELLED` | `COMPENSATED` |
| Late paid success cannot confirm after unpaid terminal fact | not confirmable | `PENDING_PAYMENT` plus one `OrderPaymentReviewRequiredV1` | `MANUAL_REVIEW` |

No scenario may create an automatic refund.

## 4. Aggregate local gate

Only after each focused group is green:

```powershell
pwsh -NoLogo -NoProfile -File `
  .\infra\docker\smoke\feature-044-purchase-saga.ps1 `
  -Scenario All
```

Then run explicit repository gates:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/order-service,services/flashsale-service -am verify

.\mvnw.cmd --batch-mode --no-transfer-progress clean verify

kubectl apply --dry-run=client -k infra/k8s/overlays/cloud

git diff --check
```

The validation ledger records command, scope, exit code/result, date, and CI/PR evidence. A checked
task without evidence is not complete.

## 5. Cloud preflight after local PASS

Cloud work is forbidden until:

```text
FEATURE_044_LOCAL_GATE=PASS
affected module verify=PASS
full monorepo verify=PASS
cloud Kustomize dry-run=PASS
```

Refresh the current branch/repository state and verify EKS/Argo before mutation:

```powershell
git switch develop
git pull --ff-only origin develop

kubectl config current-context
kubectl -n argocd get application flash-sale-cloud `
  -o custom-columns="NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status,REVISION:.status.sync.revision"
```

Expected application state is `Synced` and `Healthy` before provisioning.

## 6. Provision cloud contracts

The reviewed Phase 20 script will be extended to include:

- `flashsale.purchase.commands.v1`;
- three consumer DLTs;
- eight new Avro record schemas plus six DLT topic/record subject bindings (14 Registry subjects).

Run validation first, then explicit apply:

```powershell
pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase20-kafka-contracts.ps1

pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase20-kafka-contracts.ps1 `
  -Apply
```

The script must remain idempotent and must not delete topics or schemas. Its approved safety gate
requires all seven Payment runtime flags to be `false`. If an earlier Phase 24 setup has enabled
Stripe/Payment, use two reviewed GitOps changes: first pause Payment by setting those flags to
`false` (without changing `payment-secrets`) and wait for Argo plus a Payment Deployment restart;
after Phase 20 passes, restore the approved `true` values through a second reviewed change and
verify the Payment rollout. Do not provision Feature 044 contracts by bypassing this gate while
Payment processing is active.

## 7. One affected-service image release

Use the existing GitHub Actions **Eight-Service GitOps Delivery** workflow after the implementation
PR is merged. Select only the affected services reported by the final diff; expected primary targets
are `order-service` and `flash-sale-service`. Payment is selected only if its production code or
image inputs actually changed.

The workflow must:

1. verify selected Maven modules;
2. build immutable images;
3. push `release-<develop SHA>` tags to ECR;
4. create one image-promotion PR updating the cloud overlay.

### Required Feature 044 migration gate

When the promotion PR includes the Feature 044 Order and Flash Sale migration changes, run this
gate **after** the delivery workflow has pushed both ECR images and **before** merging the
image-promotion PR:

```powershell
pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase44-purchase-saga-migration-gate.ps1

pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase44-purchase-saga-migration-gate.ps1 `
  -Apply
```

The first command is read-only: it resolves the current remote `develop` SHA, verifies the two
`release-<develop SHA>` ECR images, checks only the Order/Flash Sale prerequisites, renders two
service-owned Liquibase Jobs, and runs a Kubernetes client dry-run. The second command creates and
waits **sequentially** for `migrate-order-purchase-saga` and then
`migrate-flash-sale-reservation-finalization`. It never reads or prints Secret values and it never
deletes or reruns an existing migration Job. If either immutable image does not exist, do not merge
the promotion PR: wait for/fix the delivery workflow first.

Only merge the promotion PR after the `-Apply` command reports `Feature 044 migration release gate:
APPLY PASS`. Then close stale competing promotion PRs.

## 8. Argo and runtime verification

```powershell
kubectl -n argocd annotate application flash-sale-cloud `
  argocd.argoproj.io/refresh=hard --overwrite

kubectl -n argocd get application flash-sale-cloud --watch

kubectl -n flash-sale rollout status deployment/order-service --timeout=300s
kubectl -n flash-sale rollout status deployment/flash-sale-service --timeout=300s

.\infra\scripts\gitops\phase21-cloud-release-verify.ps1
```

Verify actual Deployment images use the reviewed immutable release tag.

## 9. Phase 24 Stripe cloud completion

```powershell
.\infra\scripts\gitops\phase24-https-edge.ps1 -Run

.\infra\scripts\gitops\phase24-stripe-cloud.ps1 `
  -Run `
  -AdminLogin "admin@flashsale.test"
```

The extended runner must prove:

- real accepted purchase creates the Order-owned Payment request;
- Checkout Session is created through the owner API;
- valid signed Stripe webhook receives HTTP 2xx;
- durable provider receipt exists and duplicate webhook is one semantic outcome;
- Payment publishes the result through outbox/Kafka;
- Flash Sale confirms or releases the reservation;
- Order reaches the expected final status;
- no secret, token, webhook signature/body, or Checkout URL appears in output.

## 10. Rollback rehearsal

Rollback does not delete data:

1. disable new Order command production through reviewed cloud config;
2. wait for or explicitly record outstanding outbox/Saga work;
3. revert the image-promotion commit to previous immutable tags;
4. refresh Argo and verify previous Deployments;
5. confirm Saga/inbox/outbox/payment/reservation rows and topics remain intact.

### Compatibility gate before a real rollback

Before creating the reviewed GitOps image-promotion revert in step 3, run the read-only
compatibility rehearsal against the exact previous **common** Order/Flash Sale release SHA. Obtain
that SHA from the predecessor reviewed cloud image-promotion commit; it is the suffix of both
previous `release-<SHA>` image tags.

```powershell
$PriorReleaseSha = "<40-character previous common release SHA>"

pwsh -NoLogo -NoProfile -File `
  .\infra\scripts\gitops\phase44-purchase-saga-rollback-rehearsal.ps1 `
  -PriorReleaseSha $PriorReleaseSha
```

The rehearsal changes nothing. It verifies that the prior images still exist in ECR, reads their
`OrderStatus` and `ReservationStatus` enum surfaces from the matching Git revision, confirms the
expanded Feature 044 schema, and reads only aggregate terminal-state counts from the two owning
databases. It requires at least one representative terminal Order and Reservation row, but never
prints IDs, customer data, credentials, or SQL result payloads.

- `PASS` means the prior images understand every observed terminal status. It is still only a
  compatibility prerequisite: follow the five rollback steps above and verify Argo after the
  reviewed image revert.
- `Incompatible persisted terminal state` means **do not restore the prior images**. Do not drop
  tables, shrink constraints, delete Saga/inbox/outbox rows, or map values back to older states.
  Preserve data and return to spec/plan approval for an explicitly reviewed forward-compatible
  release or a separately approved data policy.

Cart Service and Notification Service are still deferred after Feature 044 succeeds.
