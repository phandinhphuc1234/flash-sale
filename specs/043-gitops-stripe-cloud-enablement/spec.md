# Feature Specification: Stripe Test-Mode Cloud Enablement

**Feature Branch**: `codex/gitops-phase24-stripe-cloud`
**Created**: 2026-08-23
**Status**: Approved for implementation — option A selected; domain/DNS inputs remain operator-supplied
**Input**: Enable the already-implemented Payment Service Stripe Checkout slice in the single AWS
development environment, without exposing card data or committing secrets.

## Problem and scope

The Payment Service is deployed to EKS but all seven runtime switches remain disabled. The
repository already contains the Payment contracts, Stripe adapter, webhook verifier, persistence,
outbox, recovery workers, Gateway route, and Phase 15 secret provisioning. Phase 24 must connect
those pieces in the cloud and prove a bounded Stripe test-mode flow. It must not silently turn on a
broken webhook path: Stripe requires an HTTPS endpoint, while the Phase 23 public AWS hostname was
HTTP-only and has now been rolled back to private `ClusterIP` Services.

### In scope

- Validate owner-managed Stripe test credentials through the existing ignored `infra/docker/.env`
  boundary and provision only the `payment-secrets` Kubernetes Secret via the reviewed Phase 15
  script.
- Enable the seven Payment switches together in the cloud ConfigMap:
  `PAYMENT_ACCEPTANCE_ENABLED`, `PAYMENT_CHECKOUT_ENABLED`, `STRIPE_ENABLED`,
  `PAYMENT_CONSUMER_ENABLED`, `PAYMENT_OUTBOX_PUBLISHER_ENABLED`, `PAYMENT_RECOVERY_ENABLED`,
  and `PAYMENT_WEBHOOK_PROCESSING_ENABLED`.
- Keep `STRIPE_MODE=test`, hosted Checkout with `card` only, PostgreSQL as durable truth, and the
  existing Kafka topics/contracts and Order-owned Saga boundary.
- Provide a bounded preflight, rollout, checkout, webhook, replay/idempotency, health, and rollback
  runbook. Evidence must be sanitized and must not contain secret values, card data, raw webhook
  bodies, full JWTs, or Checkout URLs.
- Use the external Gateway as the only ingress. The canonical cloud webhook transport must be
  HTTPS and preserve the raw body plus `Stripe-Signature` header.

### Out of scope

- Stripe live mode, real charges, refunds, disputes, payouts, subscriptions, or delayed payment
  methods.
- Storing PAN, CVC/CVV, raw provider payloads, webhook signatures, or Stripe secrets in Git,
  ConfigMaps, logs, events, or database evidence.
- Replacing the existing Payment implementation, changing Kafka contracts, changing Order Saga
  ownership, or adding a second deployment source of truth.
- Claiming PCI-DSS compliance; this remains a PCI-DSS-aware hosted-Checkout boundary only.
- Public HTTP-only webhooks or a direct Payment Service LoadBalancer.

## Human decision required

**Decision PAY-TRANSPORT-001: Option A selected.** The cloud webhook transport is a domain-backed
HTTPS Gateway edge. The concrete domain and DNS owner are operator-supplied implementation inputs;
they must be present before Terraform apply or Stripe registration.

- **A — Selected canonical cloud path**: attach a real domain to the Gateway, issue an ACM
  certificate, expose HTTPS through an AWS-managed LoadBalancer/Ingress, and register
  `https://<domain>/webhooks/v1/payments/stripe` in Stripe Test mode. This makes Phase 24 a real
  cloud flow and keeps external ingress at the Gateway.
- **B — Temporary test relay**: keep Services private and use Stripe CLI forwarding to a local
  port-forward. This validates provider handling but is not a completed cloud webhook deployment;
  Phase 24 would remain partially complete until A is implemented.

**Owner**: project owner. No live Payment flags are changed until the domain is validated, the HTTPS
edge is healthy, and the Stripe Test-mode webhook secret is provisioned.

## Functional requirements

- **FR-001**: A validation-only run MUST report the seven required flags and the presence of the
  three Stripe key names without printing values.
- **FR-002**: `-Apply` secret provisioning MUST read only the ignored local `.env` through Phase 15,
  create/update `payment-secrets`, and never stage or commit `.env` or temporary files.
- **FR-003**: All seven switches MUST be enabled as one reviewed ConfigMap change; partial enablement
  is invalid.
- **FR-004**: Payment readiness MUST be healthy only when the enabled provider has the required
  secret material and its configured Stripe test mode is coherent.
- **FR-005**: A valid `PaymentRequested.v1` MUST be consumed idempotently and produce at most one
  logical Checkout workflow for one Order.
- **FR-006**: A Checkout Session MUST be created with server-owned amount/currency/order references;
  card data MUST remain on Stripe-hosted Checkout.
- **FR-007**: The webhook endpoint MUST be HTTPS at the external boundary, verify the raw-body
  signature, durably record the provider event, acknowledge only after durable receipt, and process
  duplicates idempotently.
- **FR-008**: Payment outcomes MUST use the existing versioned Kafka result contracts and outbox;
  Order remains the Saga orchestrator.
- **FR-009**: A rollback MUST disable all seven switches, reconcile through Argo, and leave
  PostgreSQL data, Kafka topics, Secrets, and PVCs intact.

## Acceptance scenarios

1. **Preflight** — Given the cloud overlay and owner secret boundary are present, when validation
   runs, then it passes without displaying any resolved secret.
2. **Healthy enablement** — Given decision A and approved Stripe test secrets, when the ConfigMap,
   Secret, and Payment deployment reconcile, then Argo is `Synced/Healthy`, Payment readiness is
   healthy, and all seven flags are `true`.
3. **Checkout** — Given a valid Order-owned Payment request and an authenticated owner, when the
   owner starts Checkout, then a hosted card-only Session URL is returned with `no-store` and no
   card data crosses the project boundary.
4. **Webhook** — Given a Stripe test event received at the HTTPS Gateway route, when the signature
   is valid, then a durable provider receipt is created and Payment publishes one stable outcome.
5. **Replay** — Given the same provider event or client idempotency key is delivered again, when it
   is processed, then no second semantic payment effect or duplicate result is created.
6. **Invalid trust** — Given a bad signature or HTTP endpoint bypass, when a request arrives, then it
   is rejected and no Payment state changes.
7. **Rollback** — Given an enabled test-mode deployment, when the reviewed rollback is applied, then
   all seven flags are `false`, Argo is healthy, and no durable data is deleted.

## Architecture and security constraints

- Payment owns only its own PostgreSQL database and persistence evidence. It never reads another
  service's database.
- API Gateway is the sole external ingress; Payment's webhook route remains a narrow forwarded route.
- Kafka topics remain `flashsale.payment.commands.v1` and `flashsale.payment.events.v1`; no new
  business topic is introduced here.
- Stripe-hosted Checkout is the PCI-DSS-aware boundary. Raw card data, PAN, CVC/CVV, and secrets
  are never accepted or persisted by this repository.
- Secret values are owner-managed. Agents may name required keys and run approved scripts only after
  owner confirmation; they must not read, paste, or print resolved values.
- Kubernetes desired state is owned by `infra/k8s/overlays/cloud` and Argo CD; scripts are bounded
  operators, not a second deployment controller.

## Success criteria

- **SC-001**: A sanitized preflight proves all required ConfigMap keys and Secret boundaries.
- **SC-002**: Argo converges the enabled Payment deployment to `Synced/Healthy` with all seven
  switches true and no other service inadvertently enabled.
- **SC-003**: One Stripe test Checkout plus one valid webhook reaches one durable Payment outcome;
  duplicate delivery remains one semantic outcome.
- **SC-004**: No evidence artifact contains secret values, raw webhook body/signature, full JWT,
  PAN/CVC, or a Checkout URL.
- **SC-005**: Rollback returns all seven switches to false and preserves Secrets, PVCs, topics, and
  database rows.
- **SC-006**: If decision A is not approved, the feature explicitly records Phase 24 as partial and
  does not claim cloud webhook completion.

## Dependencies

- Verified Payment Service MVP specification and its existing implementation under
  `specs/021-payment-service-mvp`.
- Phase 15 secret provisioning and Phase 22 cloud guard.
- Phase 20 Kafka topics/schemas and Phase 21 release verification.
- A reviewed ADR for the HTTPS webhook edge if decision A is selected.
- Owner-provided Stripe Test-mode values: `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, and
  `STRIPE_WEBHOOK_SECRET`; values remain only in ignored `infra/docker/.env`.
