Before planning, require an Approved spec with no P0/P1 decision blocking the selected scope. The
plan must not add business behavior.

Analyze service/context ownership, source of truth, transaction and idempotency boundaries,
concurrency, ordering, retry, outbox, compensation, reconciliation, compatibility, security,
observability, migration, rollback, and acceptance-to-test mapping where applicable.

{CORE_TEMPLATE}

Before completion, confirm both Constitution Checks pass, every production dependency and omitted
test layer is justified, and every exception references the required ADR or Complexity Tracking
entry. Human reviewers own plan approval.

