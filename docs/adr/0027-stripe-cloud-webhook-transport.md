# ADR-0027: Stripe cloud webhook transport

- **Status**: Accepted — option A selected; concrete domain is an operator input
- **Date**: 2026-08-23
- **Decision owners**: Project owner, architecture reviewer, security reviewer
- **Scope**: Canonical roadmap Phase 24, development EKS environment only

## Context

Payment Service already implements hosted Stripe Checkout, signature verification, durable provider
receipts, idempotency, recovery, and an API Gateway webhook route. The Phase 23 AWS-generated NLB was
HTTP-only and has been rolled back to private `ClusterIP`. Stripe Test-mode webhook delivery needs an
HTTPS endpoint. Enabling Payment before selecting a transport would produce a checkout flow with no
reliable provider callback.

## Options

1. **Domain + ACM + HTTPS Gateway edge (recommended)** — map a development DNS name to the approved
   AWS edge, attach an ACM certificate, preserve Gateway as the only external ingress, and register
   `https://<domain>/webhooks/v1/payments/stripe` in Stripe Test mode.
2. **Stripe CLI relay** — forward Stripe events to a local port-forward. This is useful for bounded
   provider tests but is not a cloud deployment and cannot mark Phase 24 complete.
3. **HTTP-only NLB** — rejected because it cannot be the canonical Stripe webhook endpoint.
4. **Direct Payment LoadBalancer** — rejected because it bypasses the accepted Gateway boundary.

## Decision

Select option 1: a domain-backed ACM certificate and HTTPS Gateway edge. The actual domain, DNS
hosted-zone ownership, and certificate validation records are supplied by the project owner. Until
those inputs are validated and the edge is healthy, all seven Payment runtime switches remain
`false` in the cloud desired state.

## Consequences

- Option 1 requires a DNS name, ACM certificate, AWS edge configuration, and a Stripe Test-mode
  destination. It supplies a repeatable cloud path and preserves the architecture boundary.
- Option 2 requires no DNS but depends on an operator laptop and must be labelled partial.
- Neither option permits raw card data, raw webhook payload persistence, or secret values in Git.

## Guardrails

- `infra/docker/.env` remains ignored and owner-managed; Phase 15 is the only approved provisioning
  path.
- Stripe Test mode and `card` only are mandatory.
- Payment result publication remains the existing Kafka outbox flow; Order remains Saga owner.
- Rollback is a Git change that disables all seven switches and does not delete durable state.
