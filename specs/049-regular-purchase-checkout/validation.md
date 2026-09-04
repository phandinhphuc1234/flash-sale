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

## Phase 2A — additive Kafka contracts and provisioning inventory

| Task / gate | Command / scope | Result |
|---|---|---|
| T009–T013, Avro contract compile and tests | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts test` | PASS — generated SpecificRecords and all contract tests pass; V1 schemas were not modified. |
| T014, local topic script syntax | Git Bash `bash -n infra/docker/kafka/init-regular-purchase-topics.sh` | PASS — the script creates only six additive topics, never deletes a topic, and refuses an ordering-changing expansion after records exist. |
| T015–T016, provisioning-script syntax | PowerShell parser for `register-regular-purchase-schemas.ps1` and `phase20-kafka-contracts.ps1` | PASS — source and DLT TopicRecordNameStrategy subjects are enumerated with `BACKWARD_TRANSITIVE` checks. |
| Repository hygiene | scoped `git diff --check` | PASS — no whitespace error. |

No broker, Schema Registry, consumer group, subject, or Kubernetes resource was changed by this
sub-phase. Phase 20 `-Apply` remains an explicit, merge-gated operator action after all migrations
and compatible consumer images are reviewed.

## Phase 2B — expand-first migrations and machine trust

| Task / gate | Command / scope | Result |
|---|---|---|
| T017–T022, Cart migration | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartMigrationCompatibilityIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — PostgreSQL Testcontainers migration test passed; Cart revisions and the reconciliation inbox are additive. |
| T017–T022, Inventory migration | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularHoldMigrationIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — regular-hold tables, inbox, and additive outbox envelope fields applied on a fresh PostgreSQL schema. |
| T017–T022, Order migration | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseMigrationIntegrationTests,OrderSchemaMigrationIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — regular intake schema applied and seven existing schema compatibility tests passed. |
| T023–T029, exact client and internal security | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/authentication-service,services/cart-service,services/product-service,services/inventory-service,services/order-service -am "-Dtest=CartInternalSecurityConfigurationTests,ProductPurchaseQuoteSecurityTests,InventoryRegularHoldSecurityConfigurationTests,OrderServiceClientCredentialTests,OrderInternalClientConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — focused tests cover token claims, forbidden scopes, Order token propagation, and wrong/missing subject or scope at each future internal route. |
| T030, static runtime gates | PowerShell parser for Feature 049 provisioning scripts; `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config --quiet`; `kubectl kustomize infra/k8s/overlays/cloud`; `git diff --check` | PASS — scripts parse, Compose and cloud manifests render, and no whitespace error exists in scoped changes. |

### Phase 2B safety boundary

- These migrations are expand-only. Existing Flash Sale fields remain available, new runtime flags
  remain false, and no service starts a regular-purchase endpoint or consumer yet.
- No live database, Kafka broker, Schema Registry subject, EKS resource, Secret value, or image was
  created or changed during this phase.

## Phase 3A — Product purchase quotes

| Task / gate | Command / scope | Result |
|---|---|---|
| T031, focused Product quote tests | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/product-service -am -Dtest=PurchaseQuoteTests,PurchaseQuotePersistenceTests,PurchaseQuoteHttpTests,ProductPurchaseQuoteSecurityTests -Dsurefire.failIfNoSpecifiedTests=false test` | PASS — seven tests cover the 20-line bound, duplicate rejection, deterministic ID order, current price/currency/version, missing/unsellable decisions, and exact Order-only HTTP scope. |
| T031, T037–T038, Product regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/product-service -am verify` | PASS — three-module reactor, 50 Product tests and 9 common-web tests, zero failures/errors. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in Feature 049/Product quote changes. |

### Phase 3A safety boundary

- Product only reads its own catalog tables and returns current quote decisions. It does not read Cart,
  reserve Inventory, create an Order, emit an event, or expose the internal route through Gateway.
- The Product endpoint accepts only the exact `order-service` machine identity and
  `catalog.purchase-quote.read` scope. No existing Cart or Campaign capability was broadened.
