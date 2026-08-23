# Research: Stripe Test-Mode Cloud Enablement

## Findings

1. Payment already implements the desired boundary. `application.yml` exposes the seven runtime
   switches, hosted Checkout settings, webhook path `/webhooks/v1/payments/stripe`, and Stripe test
   mode. The cloud ConfigMap currently sets every switch to `false`.
2. Phase 15 already knows how to validate the ignored `.env` and provision `payment-secrets` without
   printing values. It must be invoked with explicit `-EnableStripe`; no new secret loader is needed.
3. Phase 22 and Phase 21 already inspect Payment flags and secret boundaries. They can be reused as
   post-reconcile safety gates rather than duplicating secret handling.
4. Phase 23's public NLB was intentionally rolled back. Its generated AWS hostname was HTTP-only;
   Stripe webhook registration for the canonical cloud flow needs an HTTPS URL with a certificate.
5. The Payment spec explicitly says Gateway owns external ingress, Stripe signature verification
   uses the exact raw body, and provider receipts/outbox are durable and idempotent.

## Decision matrix

| Option | What it proves | Limitation | Recommendation |
|---|---|---|---|
| Domain + ACM + HTTPS Gateway edge | Complete cloud webhook path and real Stripe delivery | Requires domain/DNS/certificate and an edge change | Recommended for Phase 24 completion |
| Stripe CLI relay + local port-forward | Provider signature/receipt/replay behavior | Not a stable cloud endpoint; operator laptop is in the path | Useful interim validation only |
| HTTP public NLB | None for Stripe webhook acceptance | Stripe will not call a plain HTTP webhook | Rejected |
| Direct Payment LoadBalancer | Bypasses Gateway ownership and security boundary | Violates accepted architecture | Rejected |

## Safe rollout sequence

1. Resolve PAY-TRANSPORT-001 and approve the corresponding ADR.
2. Validate owner secret names with Phase 15; apply only `payment-secrets`.
3. Apply the cloud ConfigMap with all seven switches in one commit; let Argo reconcile.
4. Verify readiness, flags, image digest, Kafka consumer, and no accidental public backend Service.
5. Execute the bounded Stripe test-mode Checkout/webhook/replay scenario.
6. Record sanitized evidence, then rehearse the all-flags-off rollback.

## Open risks

- Enabling consumers before the Order producer is live would be harmless but provides no business
  event; the Phase 24 test must use an approved PaymentRequested fixture or the later Saga flow.
- Stripe webhook delivery can retry or arrive out of order; receipt uniqueness and state monotonicity
  must remain the authority.
- A successful Checkout browser redirect is not payment truth; only a verified provider event or
  provider retrieval is authoritative.
- Generated NLB hostnames are not suitable for a durable HTTPS certificate strategy without a DNS
  name. The cloud route must not be marked complete on HTTP-only smoke.

