# Phase 24 Tasks: Stripe Test-Mode Cloud Enablement

## Planning and approval gate

- [x] T001 Confirm PAY-TRANSPORT-001: domain + ACM HTTPS edge selected; concrete domain remains an
  operator-supplied input.
- [x] T002 Accept ADR 0027 for the selected HTTPS webhook transport and preserve the Gateway-only
  ingress boundary.
- [x] T003 Align this spec, plan, research, data model, contract, and quickstart with the existing
  Payment Service and Phase 15/21/22 controls.

## Safe implementation

- [ ] T004 Add a bounded Phase 24 preflight/rollback script; it must not print Secret values or
  mutate live state unless `-Apply` is explicit.
- [ ] T005 Add HTTPS Gateway desired state for option A, or document the option-B relay as partial;
  keep Payment and all backend Services private.
- [ ] T006 Change all seven Payment switches together in the cloud ConfigMap; keep test mode and
  existing topic names unchanged.
- [ ] T007 Add/adjust static tests for the ConfigMap, secret boundaries, route preservation, and
  all-flags-together invariant.

## Verification

- [ ] T008 Run Kustomize dry-run, Phase 15 validation/apply with owner confirmation, Phase 22 guard,
  and Argo rollout checks.
- [ ] T009 Run bounded Stripe Test-mode Checkout and valid webhook through the approved HTTPS edge;
  verify receipt, outbox/result, idempotency, and no raw sensitive data in evidence.
- [ ] T010 Replay the same webhook/client key and verify one semantic effect; test invalid signature
  rejection and provider-recovery visibility.
- [ ] T011 Revert to all flags false and verify Argo health plus preservation of Secret/PVC/topic/
  database resources.
- [ ] T012 Record sanitized validation evidence, update the roadmap, and mark Phase 24 complete only
  when option A's HTTPS webhook has actually delivered a test event.
