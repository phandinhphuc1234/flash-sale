# Research: Cloud Application Rollout

## Decision 1 — Keep health authorization independent from Payment acceptance

**Decision**: Always load the Payment default security chain and permit only Actuator health/info/
prometheus paths. Load the authenticated Payment API chain only when acceptance is enabled.

**Rationale**: The live Pod had a successful Spring startup and database connection but returned
`401` for `/actuator/health/liveness` because `PaymentSecurityConfiguration` was disabled by
`PAYMENT_ACCEPTANCE_ENABLED=false`, allowing Spring Security's default chain to protect every path.

**Alternatives considered**: Changing the Kubernetes probe to TCP would hide HTTP authorization
regressions; setting acceptance true would enable business Payment components during bootstrap.

## Decision 2 — Operator-gated application rollout

**Decision**: Use an explicit PowerShell runner with validation-only default and `-Apply` for live
changes.

**Rationale**: This matches the Phase 15/16 safety boundary and prevents missing secrets, platform
resources, or migrations from being diagnosed as application failures.

**Alternatives considered**: Adding the rollout to CI/Argo now would couple deployment to an
environment that is still being bootstrapped.
