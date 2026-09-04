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

## Phase 3B-1 — Inventory regular-hold core

| Task / gate | Command / scope | Result |
|---|---|---|
| T032, T039–T040, focused domain/application tests | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularStockHoldTest,RegularStockHoldApplicationServiceTest,CampaignAllocationApplicationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — nine focused tests cover the exact five-minute TTL, ±90-second requested-at acceptance boundary, canonical lines, legal terminal transitions, regular availability, a one-time physical deduction, stock movement, and unchanged campaign-allocation safety. |
| T033, T041–T042, PostgreSQL HTTP/concurrency compatibility | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularStockHoldCompatibilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — six PostgreSQL Testcontainers tests cover Order-only HTTP authentication, initial/replay/conflict identity semantics, all-or-nothing multi-line validation, 91-second skew rejection, deterministic lock contention without oversell, and one confirmed movement/deduction. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in the scoped Inventory/Feature 049 changes. |

### Phase 3B-1 safety boundary

- The Inventory HTTP endpoint remains disabled by default and is callable only by the documented
  `order-service` machine identity and scope; it is not routed through Gateway.
- Core confirmation is durable and atomic locally, but its Kafka command listener and fact outbox
  publisher are still disabled and remain the next checked tasks (T043–T044).

## Phase 3B-2 — Inventory confirm inbox and fact outbox

| Task / gate | Command / scope | Result |
|---|---|---|
| T043, strict inbound command mapping and inbox replay | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=ConfirmRegularHoldAvroMapperTest,RegularHoldCommandProcessingServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — five focused tests validate exact Order envelope/key/source position, SHA-256 payload identity, conflict rejection, one local confirm, and duplicate-command requeue with the same result event identity. |
| T044, fact outbox relay and schema compatibility | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=InventoryCleanArchitectureTest,InventoryRegularHoldKafkaConsumerConfigurationTests,ConfirmRegularHoldAvroMapperTest,RegularHoldCommandProcessingServiceTest,RegularHoldOutcomeAvroMapperTest,RegularHoldOutboxDispatchPersistenceAdapterTest,RegularHoldMigrationIntegrationTests,RegularStockHoldCompatibilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 19 tests cover the Clean/Hex boundary, consumer-specific DLT partition/routing and bounded retry, fresh Liquibase schema including normalized fingerprint columns, PostgreSQL hold compatibility/concurrency, regular-hold-only lease claim, and exact V1 confirmed fact mapping. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Inventory/Feature 049 changes. |

### Phase 3B-2 safety boundary

- The command consumer, outbox scheduler, and producer configuration remain disabled unless the
  reviewed `flashsale.inventory.regular-hold.*` flags are explicitly enabled.
- The inbox and fact outbox commit in the same local Inventory transaction as hold confirmation and
  physical stock movement. A duplicate command requeues the existing fact with its original
  `eventId`; it never creates another movement or another fact identity.
- The regular-hold publisher claims only `REGULAR_STOCK_HOLD` outbox rows, so it cannot relay or
  alter the existing Campaign allocation publisher's events.

## Phase 3C-1 — Order source and participant domain model

| Task / gate | Command / scope | Result |
|---|---|---|
| T035, T045, Order and Saga domain regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=OrderDomainTests,RegularOrderDomainTests,PurchaseSagaDomainTests,PurchaseSagaLateSuccessTests,RegularHoldPaidSagaTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 23 selected Order tests passed with zero failures/errors: existing Flash Sale Order/Saga construction and late-success behavior remain intact; Buy Now is one line only; Cart lines are bounded, canonical, and immutable; regular hold payment moves through `CONFIRMING_STOCK` and completes only for the exact hold and Payment identity. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Order/Feature 049 changes. |

### Phase 3C-1 safety boundary

- This sub-phase is pure Order-domain preparation. It adds no public endpoint, JPA mapping, migration execution, Feign call, Kafka listener, outbox relay, or live infrastructure mutation.
- Existing Flash Sale factories and reservation-specific Saga transitions retain their public signatures and state names. Regular orders use explicit `PurchaseSource` and `StockParticipantType`; their legacy campaign/reservation references are absent by invariant instead of being used to infer behavior.

## Phase 3C-2 — Cart intake persistence correction

| Task / gate | Command / scope | Result |
|---|---|---|
| T045A, forward-only Order migration | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseMigrationIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — two PostgreSQL Testcontainers tests applied changesets `003` and `004`; a Cart `RECEIVED` request may persist before Cart snapshot resolution, a `SNAPSHOT_VALIDATED` request without Cart identity is rejected, and a snapshot-bound request with Cart ID/version succeeds. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Order migration and Feature 049 artifacts. |

### Phase 3C-2 safety boundary

- Changeset `003` remains byte-for-byte unchanged, so any already-applied Liquibase checksum stays valid. Changeset `004` is forward-only and only replaces the check constraint.
- The rule never accepts a browser-selected Cart ID. Order obtains Cart identity from the exact-subject internal Cart snapshot and records it only with the successful state transition.

## Phase 3C-3 — Regular-purchase intake domain

| Task / gate | Command / scope | Result |
|---|---|---|
| T046, regular request domain and Order regression | `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseRequestDomainTests,RegularOrderDomainTests,RegularHoldPaidSagaTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 13 selected tests passed with zero failures/errors. The domain canonicalizes browser-supplied Cart lines without a Cart ID, preserves the fingerprint after owner-bound Cart snapshot identity is attached, enforces shopper/key replay versus conflict, and prevents a stock-held request from becoming a business rejection. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Feature 049 changes. |

### Phase 3C-3 safety boundary

- The new aggregate is Java-only domain code. It does not make an HTTP call, depend on JPA, write a database row, publish Kafka, or expose a public endpoint.
- Inventory remains the owner of the precise five-minute hold start on its own clock. Order records the returned expiry and derives the exact 30-second-earlier Payment deadline without treating an expired or ambiguous hold as a business rejection.

## Phase 3C-4 — Regular purchase atomic persistence

| Task / gate | Command / scope | Result |
|---|---|---|
| T047, regular persistence and Order regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchasePersistenceIntegrationTests,RegularPurchaseMigrationIntegrationTests,RegularPurchaseRequestDomainTests,RegularOrderDomainTests,RegularHoldPaidSagaTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 18 selected tests passed with zero failures/errors. PostgreSQL Testcontainers verifies an accepted Buy Now request atomically persists intake, Order/line, Saga, `OrderCreatedV2` (event version 2), and `PaymentRequestedV1`; a same shopper/idempotency key returns the existing intake without creating duplicate durable state. |
| Forward-only migration constraint | `RegularPurchaseMigrationIntegrationTests` against PostgreSQL Testcontainers | PASS — changeset `005` accepts only `OrderCreatedV2` at event version 2 and retains version 1 for legacy `OrderCreated`/command event types. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Order/Feature 049 changes. |

### Phase 3C-4 safety boundary

- The acceptance transaction contains only Order-owned PostgreSQL writes. Product, Cart, Inventory, Payment, and Kafka calls remain outside it; Kafka publication stays an outbox responsibility.
- Changeset `005` is additive and forward-only. It does not edit the previously applied version-1 check constraint, preserving Liquibase checksum safety for environments that already ran earlier changesets.
- The idempotency advisory lock is scoped to the authenticated shopper and idempotency key. It prevents duplicate accepted state for that identity while preserving an existing request as the replay source.

## Phase 3C-5 — Order internal Product and Inventory clients

| Task / gate | Command / scope | Result |
|---|---|---|
| T048, T049, client boundary and architecture regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=ProductPurchaseQuoteClientAdapterTests,InventoryRegularHoldClientAdapterTests,OrderInternalClientConfigurationTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 11 selected tests passed with zero failures/errors. Product requests are bounded, deduplicated, sorted batches and reject malformed/identity-mismatched results. Inventory preserves the same hold/purchase request/order identities and maps a transport timeout to an explicit ambiguous outcome instead of a stock rejection. |
| Dependency/configuration scope | Existing Order OpenFeign/OAuth2 dependencies and `order-product`/`order-inventory` timeout configuration | PASS — no new dependency. Scoped clients use the existing Order client-credentials token registration, `X-Trace-Id`, 300 ms connect timeout, bounded read timeout, and `Retryer.NEVER_RETRY`. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Order/Feature 049 changes. |

### Phase 3C-5 safety boundary

- Feign and remote DTOs are confined to `adapter/out/client`; the regular-purchase application ports expose only Order-owned records and sanitized failure classifications.
- Neither adapter forwards a shopper/admin bearer token. The existing Order machine identity is added by its scoped interceptor; no secret or authorization header is logged.
- The Inventory adapter never invents a new hold identity after an uncertain response. T050 recovery reuses the durable IDs already carried by the command.

## Phase 3C-6 — Buy Now checkpoint orchestration

| Task / gate | Command / scope | Result |
|---|---|---|
| T050, Buy Now workflow and dependent client/architecture regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseCheckoutServiceTests,ProductPurchaseQuoteClientAdapterTests,InventoryRegularHoldClientAdapterTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 13 selected tests passed with zero failures/errors. Buy Now persists `PRODUCT_VALIDATED` and `HOLD_ACQUIRED` checkpoints around the two HTTP decisions, then atomically accepts the Order/Saga/outbox. Accepted equivalent replays make no downstream call; price/stock business decisions become durable rejection before a hold; uncertain dependency outcomes keep their existing checkpoint for same-ID recovery. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Order/Feature 049 changes. |

### Phase 3C-6 safety boundary

- `RegularPurchaseCheckoutService` has no transaction annotation and calls only transactional persistence-port operations between synchronous Product/Inventory calls. It never holds an Order database transaction across the network.
- A replay reuses the persisted Order and hold identities. An Inventory timeout remains `INVENTORY_HOLD_AMBIGUOUS`, so the application neither rejects it as out of stock nor generates a second hold identity.
- T051 remains responsible for the public HTTP mapping, feature flag response, headers, and structured `PRICE_CHANGED` response; this phase adds no controller or new public route.
