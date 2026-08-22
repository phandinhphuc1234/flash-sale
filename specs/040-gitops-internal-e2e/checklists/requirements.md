# Specification Quality Checklist: Internal Authenticated Cloud E2E

**Purpose**: Validate that the canonical Phase 22 smoke is safe, contract-driven, and independently
verifiable before implementation.

**Created**: 2026-08-22

**Feature**: [spec.md](../spec.md)

## Scope and safety

- [x] Canonical Phase 22 is distinguished from the existing read-only Phase 22 cloud guard.
- [x] Internal Gateway port-forward is the only ingress; direct service/database/cache/broker access
  is prohibited.
- [x] Product, Inventory, Campaign, Flash Sale, and Order ownership boundaries are explicit.
- [x] Payment/Stripe and public Gateway exposure remain out of scope.
- [ ] Admin credential source is approved by the project owner.
- [ ] Business fixture and Inventory initialization path is approved by the project owner.
- [ ] Order assertion strategy is approved by the project owner.

## Contract completeness

- [x] Auth register/login routes and shopper/admin role behavior are captured.
- [x] Product draft/composition/publish headers and versioning are captured.
- [x] Inventory stock adjustment contract and idempotent request ID are captured.
- [x] The current transport-deferred Inventory initialization boundary is recorded.
- [x] Campaign create/item/schedule/activate versioning and idempotency are captured.
- [x] Flash Sale reservation idempotency, trace, owner query, and 202 acceptance are captured.
- [x] Order is asserted through its owner query after `PurchaseAcceptedV1`; no POST Order is assumed.

## Operational validation

- [x] Bounded per-call and whole-run timeouts are required.
- [x] Secrets, JWTs, cookies, and raw passwords are excluded from output.
- [x] Port-forward and temporary files are cleaned up on every exit path.
- [x] Validation-only mode is non-mutating.
- [ ] Live smoke evidence is recorded in `validation.md` after implementation.

## Notes

Production code and the runner must not be implemented until the three unchecked human decisions
are resolved and the plan/tasks artifacts are approved.
