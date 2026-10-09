# Storefront Completion Roadmap (P0-P2)

**Status**: Proposed program roadmap  
**Date**: 2026-10-09  
**Decision**: The backlog is consolidated into exactly three umbrella specifications—one per
priority—rather than one specification per page or capability.

## 1. Why three specifications

The storefront backlog spans UI reliability, Flash Sale discovery, account security, shopper
convenience, and Operations Console workflows. One specification for everything would be too risky,
while thirteen small specifications would create unnecessary process overhead for this portfolio.

The approved direction is therefore:

```text
P0 — specs/056-storefront-p0-completion
     Storefront trust + public Flash Sale experience

P1 — specs/057-storefront-p1-experience
     Purchase clarity + account recovery + Wishlist

P2 — specs/058-storefront-p2-operations
     Category + notifications + reviews + read-only support/dashboard
```

Each umbrella spec contains independently testable user stories. Planning and tasks must keep those
stories in dependency order, and implementation may still use multiple reviewable PRs. One spec does
not mean one large code change or one risky deployment.

## 2. Delivery order

### P0 — Storefront completion

Specification: [`../../specs/056-storefront-p0-completion/spec.md`](../../specs/056-storefront-p0-completion/spec.md)

1. Loading, error, and not-found page boundaries.
2. Remove inert Newsletter/Wishlist/address controls; add real static Help/Contact/Privacy pages.
3. Add public Campaign discovery/detail contracts and Gateway exposure.
4. Add `/flash-sale` and campaign detail storefront pages.
5. Reuse and regression-test the existing reservation → Order → Payment journey.

P0 intentionally excludes physical shipping and exact remaining-stock claims. It is the next active
feature after owner approval.

### P1 — Shopper experience

Specification: [`../../specs/057-storefront-p1-experience/spec.md`](../../specs/057-storefront-p1-experience/spec.md)

1. Friendly Order/Payment/reservation progress timeline using existing states.
2. Secure forgot/reset-password journey after the owner resolves reset TTL/session policy.
3. Durable authenticated Wishlist with current Product presentation.

P1 does not add shipping states, refunds, product reviews, or external notification channels.

### P2 — Commerce operations

Specification: [`../../specs/058-storefront-p2-operations/spec.md`](../../specs/058-storefront-p2-operations/spec.md)

1. Category administration and category selection for Product composition.
2. In-app Order/Payment notification inbox.
3. Verified Product ratings/reviews after eligibility policy is approved.
4. Read-only admin Order search/detail.
5. Bounded Order/Campaign operational counts; no accounting/revenue claims.

P2 excludes refund/cancellation/state mutation, external messaging channels, rich review media, and
financial analytics.

## 3. Cross-spec dependency

```text
Feature 056 P0
  ├── resilient page shell used by all later pages
  └── public Flash Sale experience
          │
          ▼
Feature 057 P1
  ├── purchase progress
  ├── account recovery
  └── Wishlist
          │
          ▼
Feature 058 P2
  ├── category administration
  ├── notification inbox
  ├── verified reviews
  └── read-only support/dashboard
```

P1 planning starts only after P0 is stable. P2 planning may research independently, but production
implementation waits until P0 and the relevant P1 account/navigation foundations are stable.

## 4. Shared rules

- Browser traffic enters through API Gateway only.
- No UI calls `/internal/**`, service ports, OAuth client credentials, or webhooks.
- Visible controls must work or be absent/clearly unavailable.
- Backend contracts are approved before frontend integration.
- Order/Payment/stock truth is never calculated or guessed in the browser.
- Service databases, JPA models, and business ownership remain isolated.
- One umbrella spec may produce multiple task groups and PRs; each group remains independently
  testable and reviewable.

## 5. Validation baseline

- approved spec, plan, and tasks before production changes;
- focused frontend tests and production build for storefront changes;
- affected Maven module verification and contract/security tests for backend changes;
- existing reservation/Order/Payment regression for purchase-adjacent work;
- responsive/accessibility review at 375, 768, 1024, and 1440 pixels;
- bounded load/replay tests where public lists or event consumers are added;
- Kubernetes dry-run only when deployment manifests change;
- validation evidence recorded per completed task group.

## 6. Decisions still required

| Spec | Blocking decision |
|---|---|
| P0 / 056 | None beyond owner approval; physical shipping is excluded from this release |
| P1 / 057 | Reset authorization lifetime and whether successful reset revokes all sessions |
| P2 / 058 | Notification retention/read expiry |
| P2 / 058 | Verified-review eligibility and contribution/edit/delete rule |

## 7. Active execution order

1. Approve/clarify the three specifications.
2. Keep `.specify/feature.json` on `specs/056-storefront-p0-completion`.
3. Plan and task P0 only.
4. Implement P0 in coherent user-story task groups with validation between groups.
5. Move the active pointer to P1, then P2, only after the preceding priority is accepted.
