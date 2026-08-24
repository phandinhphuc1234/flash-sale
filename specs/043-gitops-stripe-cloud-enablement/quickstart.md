# Phase 24 quickstart (operator-controlled)

Do not run the enablement steps until PAY-TRANSPORT-001 is resolved and the HTTPS edge is ready.
The project owner supplies Stripe Test-mode values only in ignored `infra/docker/.env`; never paste
them into chat or commit them.

## 1. Start on a clean branch

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c codex/gitops-phase24-stripe-cloud
```

## 2. Validate the secret boundary (no values printed)

```powershell
.\infra\scripts\gitops\phase15-secrets.ps1 -EnableStripe
```

The owner must ensure these names are present in the ignored file before this command can pass:
`STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`. The script must not be
changed to accept command-line secret values.

## 3. Provision only after owner confirmation

```powershell
.\infra\scripts\gitops\phase15-secrets.ps1 -EnableStripe -Apply
```

## 4. HTTPS registration

For the recommended cloud path, configure the approved domain/ACM HTTPS Gateway edge, then create a
Stripe Test-mode webhook destination for:

```text
https://<approved-domain>/webhooks/v1/payments/stripe
```

Select the existing Checkout events required by the Payment spec. Do not use the old HTTP-only AWS
generated hostname. For interim local validation only, use Stripe CLI forwarding and record Phase
24 as partial.

For this project the certificate covers `flashsale123.tech` and `*.flashsale123.tech` in
`ap-southeast-2`. Terraform outputs the DNS validation CNAMEs; add them manually in Get.Tech. Do not
create a Route 53 Hosted Zone or Route 53 record.

## 5. Enable and reconcile

After the Phase 24 PR is reviewed and merged:

```powershell
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
kubectl -n argocd annotate application flash-sale-cloud argocd.argoproj.io/refresh=hard --overwrite
kubectl -n argocd get application flash-sale-cloud --watch
```

Verify Payment readiness and all seven flags before attempting Checkout. Never use `kubectl get
secret -o yaml` in a transcript.

## 6. Run the bounded Stripe cloud smoke

The runner reuses the Phase 22 Order fixture, then continues through Payment Checkout. Validation
mode is non-mutating:

```powershell
.\infra\scripts\gitops\phase24-stripe-cloud.ps1 -AdminLogin "admin@flashsale.test"
```

Run mode prompts for the existing `ROLE_ADMIN` password, creates a Checkout Session for the
Order-owned Payment, opens the hosted Checkout page without printing its URL, and waits for the
operator to complete it with a Stripe test card (for example `4242 4242 4242 4242`). It then checks
the HTTPS webhook, duplicate acknowledgement, Payment result, Kafka outbox offset, and final Order
identity:

```powershell
.\infra\scripts\gitops\phase24-stripe-cloud.ps1 `
  -Run `
  -AdminLogin "admin@flashsale.test"
```

The script reads Stripe keys only from the ignored `infra/docker/.env`; it never prints secret
values, JWTs, Checkout URLs, Stripe identifiers, signatures, or raw webhook bodies. A successful
rollout alone is not evidence of Stripe delivery; T009 and T010 remain open until this bounded
run completes.

## 7. Rollback

Revert the ConfigMap commit, let Argo reconcile, and verify all seven flags are false. Keep the
Secret and database evidence; do not delete PVCs, Kafka topics, or Payment rows.
