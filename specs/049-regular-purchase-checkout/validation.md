# Validation Evidence: Regular Purchase Checkout

This ledger records only sanitized validation evidence. Never record database credentials, OAuth
client secrets, JWTs, Authorization headers, Stripe identifiers, Checkout URLs, provider payloads,
or business rows here.

## Approval — 2026-09-03

| Gate | Result | Evidence / boundary |
|---|---|---|
| Specification | PASS | `spec.md` is approved and contains no unresolved clarification marker. |
| Plan | PASS | `plan.md` is approved for implementation. |
| Tasks | PASS | The project owner approved `tasks.md` and authorized Phase 1 implementation. |

## Phase 1 — Setup and disabled runtime boundaries

| Task / gate | Command / scope | Result |
|---|---|---|
| T001, dependency compile | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/inventory-service,services/order-service -am -DskipTests compile` | PASS — six-module reactor, exit 0. |
| T001–T005, affected module verification | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/inventory-service,services/order-service -am verify` | PASS — six-module reactor, exit 0; Cart 34, Order 134, Inventory 29, contracts 21, and common-web 9 tests with zero failures/errors. |
| T006, local render | `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config --quiet` | PASS — configuration rendered with all Feature 049 runtime flags disabled. |
| T007, cloud render | `kubectl kustomize infra/k8s/overlays/cloud` | PASS — cloud resources rendered; no live apply executed. |
| T007, Secret script syntax/boundary | PowerShell parser for `infra/scripts/gitops/phase15-secrets.ps1` plus static key-name checks | PASS — Cart and Order client Secret names are provisioned without reading or printing values. |
| T008, bounded runner | `pwsh -NoLogo -NoProfile -File infra/docker/smoke/feature-049-regular-purchase.ps1 -Scenario Static` | PASS — `FEATURE_049_STATIC=PASS`; no runtime state read or changed. |
| Repository hygiene | exact unresolved-marker scan and scoped `git diff --check` | PASS — no unresolved clarification token and no whitespace error in Feature 049 changes. |

### Phase 1 regression found and corrected

Adding Spring Kafka to Inventory caused its listener conversion service to inspect every Spring
`Converter` bean. The existing lambda-based JWT converter erased its generic source and target
types, so the Inventory application context could not start. It is now a concrete parameterized
`Converter<Jwt, AbstractAuthenticationToken>`; the final Inventory reactor verification passed.

## Safety boundary

- Every new regular-purchase entry point, hold worker, outbox publisher, recovery worker, and Cart
  reconciliation consumer remains disabled by default.
- Phase 1 does not create database tables, Kafka topics, Schema Registry subjects, application
  workloads, ECR images, or live Kubernetes resources.
- Runtime Secret values remain in ignored operator input and generated Kubernetes Secrets; this
  repository stores only Secret names and empty placeholders.
- The cloud overlay currently has no Cart Deployment/Service image workload. Its ConfigMap and
  Secret boundary are prepared in Phase 1; cloud workload/ECR/delivery ownership must be completed
  before the Phase 8 cloud rollout gate.
