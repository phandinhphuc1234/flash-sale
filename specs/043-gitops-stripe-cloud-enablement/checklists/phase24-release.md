# Phase 24 release checklist

## Approval

- [ ] HTTPS transport decision recorded; HTTP-only NLB is not used for Stripe.
- [ ] ADR 0027 accepted.
- [ ] Payment image and Kafka contracts are the reviewed versions.

## Secrets and safety

- [ ] Owner confirmed required Stripe key names in ignored `.env`.
- [ ] Phase 15 validation passes; `-Apply` was explicit.
- [ ] No Secret values, raw webhook bodies, signatures, cards, JWTs, or Checkout URLs appear in
  logs/evidence.

## Runtime

- [ ] All seven flags changed together.
- [ ] Argo `Synced/Healthy`; Payment readiness healthy.
- [ ] Gateway is the only external ingress; backend Services remain private.

## Stripe test

- [ ] Hosted card-only Checkout created.
- [ ] HTTPS webhook verified and durably acknowledged.
- [ ] Duplicate webhook/client key replayed with one semantic effect.
- [ ] Payment result/outbox converged and evidence was sanitized.

## Rollback

- [ ] All seven flags restored to `false`.
- [ ] Secrets, PVCs, Kafka topics, and database rows preserved.
- [ ] Roadmap status reflects the actual boundary (complete or partial).

