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

## Phase 3C-7 — Buy Now HTTP boundary and compatible Order reads

| Task / gate | Command / scope | Result |
|---|---|---|
| T051–T052, Buy Now web, Order read, security, and architecture regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseControllerTests,OrderQueryControllerTests,OrderQueryServiceTests,OrderPublicSecurityTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 16 selected tests passed with zero failures/errors. New Buy Now responses prove owner derivation from JWT, `201`/replay `200`, `Location`, idempotency/trace/no-store headers, safe price-conflict facts, and existing shared error envelopes. Order reads retain Flash Sale fields and add source/stock metadata; anonymous Buy Now is rejected by the existing Order bearer boundary. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Feature 049 changes. |

### Phase 3C-7 safety boundary

- The controller accepts only a shopper JWT subject and never accepts a shopper, Order, hold, Cart, provider, or secret identity from the browser body.
- The only endpoint-specific error projection is the approved `PRICE_CHANGED` extension; all other failures remain the established Order `ApiErrorResponse` envelope with no raw downstream body.
- The Order security matcher already covers every `/api/v1/orders/**` route; the new security regression proves Buy Now cannot bypass that bearer boundary. No Gateway routing or cloud state changed.

## Phase 3C-8 — Regular Order outbox relay contracts

| Task / gate | Command / scope | Result |
|---|---|---|
| T053, regular outbox mapper/dispatcher/architecture regression | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=OrderCreatedV2AvroMapperTests,ConfirmRegularStockHoldAvroMapperTests,OrderOutboxEventTypeDispatcherTests,PaymentRequestedPublisherTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 12 selected tests passed with zero failures/errors. Dispatcher routes additive `OrderCreatedV2` and `ConfirmRegularStockHold` independently; V2 validates regular source/hold/deadline/line snapshot facts, while `PaymentRequestedV1` remains unchanged. |
| Repository hygiene | `git diff --check` | PASS — no whitespace error in scoped Feature 049 changes. |

### Phase 3C-8 safety boundary

- V1 Flash Sale `OrderCreated` routing and mapper remain unchanged. `OrderCreatedV2` publishes to the same approved Order topic under its new Avro record-name subject.
- The regular confirm publisher only relays an already durable outbox command and preserves the existing Order key, purchase-request correlation, payment causation, and trace headers. T054 owns writing that command after a verified Payment result.
- No broker, Registry, consumer, scheduler, database, cloud resource, or runtime flag was changed.

### Phase 3C-9 — Regular hold confirmation consumer (T054)

| Task / gate | Command / scope | Result |
|---|---|---|
| T054 mapper, transaction, replay, conflict, and architecture regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=ConfirmRegularStockHoldAvroMapperTests,RegularStockHoldConfirmedAvroMapperTests,RegularHoldConfirmationIntegrationTests,PaymentSuccessTransitionIntegrationTests,OrderArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 14 selected tests passed with zero failures/errors. Docker Desktop was restarted before the run; Testcontainers started PostgreSQL 17. The integration tests prove Payment success writes a regular hold command with `correlationId=purchaseRequestId` and `causationId=PaymentSucceeded.eventId`; a valid Inventory confirmation atomically completes Order/Saga, records the inbox, and emits exactly one `OrderConfirmedV2`; exact replay is idempotent; mismatched lines and command identity leave no receipt or terminal outbox fact. |
| T054 compile and repository hygiene | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am -DskipTests compile` and `git diff --check` | PASS — compile succeeded; no whitespace errors in the scoped Feature 049 changes. |

### Phase 3C-9 safety boundary

- The Kafka mapper validates topic, producer, record version, aggregate identity, key, correlation, status, item bounds, and canonical fingerprint before entering the application port.
- Saga, Order, inbox receipt, and `OrderConfirmedV2` outbox are committed in one local transaction; the consumer acknowledges only after that transaction succeeds.
- Exact event replay is returned as `REPLAYED`; lower aggregate versions are recorded as stale; conflicting same-version or identity/line content is rejected as non-retryable and routed to the result DLT.
- No runtime flag was enabled and no Kubernetes/cloud state was changed by this task.

### Phase 3C-10 — Buy Now paid local smoke (T055)

| Task / gate | Command / scope | Result |
|---|---|---|
| T055, `BuyNowPaid` local end-to-end scenario | `.\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario BuyNowPaid -TimeoutSeconds 1800` with the local Docker Compose stack | PASS — `FEATURE_049_BUY_NOW_PAID=PASS`. Buy Now acceptance and exact idempotent replay returned the original Order; a real Stripe test Checkout was completed; the signed webhook and duplicate replay were acknowledged; Payment event, regular-hold command, and regular-hold result Kafka offsets advanced; one regular hold was confirmed with exactly one physical deduction; Order became `CONFIRMED`; Cart fingerprint was unchanged. |
| Secret and output boundary | Same run | PASS — no Secret values, bearer tokens, Checkout URLs, provider payloads, or shopper identities were printed. |
| Runtime safety | Docker Compose local stack | PASS — no Kubernetes/cloud mutation; Payment deadline and provider reconciliation remained unchanged. |

### Phase 3C-10 safety boundary

- The smoke uses the real hosted test Checkout state before sending the signed webhook, so an unpaid or expired Stripe Session cannot be marked paid by a fabricated payload.
- Replay assertions cover both the Buy Now idempotency key and duplicate webhook delivery; downstream completion is observed through Payment, Kafka, Inventory, Order, and Cart read-only checks.
- The run was completed against the restarted local Docker stack after service-owned migrations; no database reset or direct business-state mutation was used.

### Phase 3C-11 — US1 module verification (T056)

| Task / gate | Command / scope | Result |
|---|---|---|
| T056 module verification | `./mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts,services/product-service,services/inventory-service,services/order-service,services/payment-service,services/api-gateway -am verify` | PASS — Maven reactor completed with `BUILD SUCCESS` in 07:56. Contract, API Gateway, Product, Order, Payment, and Inventory modules all reported `SUCCESS`; no test failures or errors were reported. |
| T056 Buy Now paid smoke | `./infra/docker/smoke/feature-049-regular-purchase.ps1 -Scenario BuyNowPaid -TimeoutSeconds 1800` | PASS — recorded above as `FEATURE_049_BUY_NOW_PAID=PASS`. |
| Inventory compatibility cleanup regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=CampaignAllocationCompatibilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 7 tests passed with zero failures/errors after including the Feature 049 hold tables in the test cleanup order. |
| Repository hygiene | `git diff --check` | PASS — no whitespace errors in the scoped Feature 049 changes. |

### Phase 3C-11 safety boundary

- The only source change in the verification fix is test cleanup ordering: child hold rows and hold inbox rows are truncated before the referenced Inventory rows. No production schema, runtime flag, or business behavior changed.
- The full reactor verification exercises the already-approved US1 implementation and does not apply Kubernetes/cloud state or expose secrets.

### Phase 4 — Cart checkout foundation and reconciliation (T057–T073)

| Task / gate | Command / scope | Result |
|---|---|---|
| T057–T058, Cart revisions and owner-safe snapshot | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartCheckoutRevisionTests,CartCheckoutSnapshotTests,CartReconciliationPersistenceIntegrationTests,ReconcilePurchasedCartSnapshotAvroMapperTests,CartArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 13 selected tests passed with zero failures/errors. Cart/item revisions, delete/re-add behavior, exact snapshot reads, owner isolation, reconciliation replay, and Avro mapping are covered. |
| T060, Cart checkout application boundary | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=CartCheckoutUseCaseTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 4 tests passed with zero failures/errors. Duplicate variants, mixed currencies, foreign snapshots, price changes, and all-or-nothing insufficient stock are rejected before durable Order state. |
| T062–T073, Cart checkout implementation and contract regression | Cart/Order focused suites plus architecture tests | PASS — Cart and Order revisions, owner-safe snapshot, exact checkout comparison, multi-item hold orchestration, replay semantics, reconciliation outbox/consumer, and frontend/API documentation are implemented and compile-tested. |
| Cart/Order module verification | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/order-service -am verify` | PASS — Maven reactor completed with `BUILD SUCCESS`; Cart reported 47 tests passed, Order reported 183 tests with zero failures/errors (8 skipped by existing integration-test prerequisites). |
| Smoke runner syntax/static gate | PowerShell parser check and `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario Static` | PASS — `FEATURE_049_STATIC=PASS`; the CartPaid and CartEditedWhilePaying scenarios are renderable and keep runtime feature flags scoped to the local process. |
| Runner no-build mode | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario PriceChanged -SkipBuild -TimeoutSeconds 1800` | BLOCKED — the local Docker Desktop Linux engine returned HTTP 500 on `/_ping` before Compose could start. No application, database, Kafka, or Secret state was changed by this attempt. |
| Order regular-purchase wiring regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am test` | PASS — 183 Order tests and 9 common-web/25 Avro prerequisite tests completed with zero failures/errors (8 existing prerequisite skips). Removing duplicate interface aliases leaves one concrete checkout bean for both Buy Now and Cart providers. |
| T074 PriceChanged live negative smoke | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario PriceChanged -SkipBuild -TimeoutSeconds 1800` | PASS — `FEATURE_049_CART_PRICECHANGED=PASS`; stale price was rejected before Payment/Inventory and Cart contents remained unchanged. The local process supplied the existing Order machine Secret without recording its value. |
| T074 InsufficientStock live negative smoke | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario InsufficientStock -SkipBuild -TimeoutSeconds 1800` | PASS — `FEATURE_049_CART_INSUFFICIENTSTOCK=PASS`; a valid two-unit Cart was rejected by Inventory with `INSUFFICIENT_STOCK` before Payment/hold completion and Cart contents remained unchanged. |
| T074 CartPaid live smoke | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario CartPaid -SkipBuild -TimeoutSeconds 1800` | PASS — `FEATURE_049_CART_PAID=PASS`; two immutable Cart lines produced one Order/Payment, exact checkout replay returned the original Order, signed Stripe webhook/replay converged through Kafka, and confirmed reconciliation removed both unchanged lines. Secret values, tokens, Checkout URLs, provider payloads, and shopper identities were withheld. |
| T074 CartEditedWhilePaying live smoke | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-049-regular-purchase.ps1 -Scenario CartEditedWhilePaying -SkipBuild -TimeoutSeconds 1800` | PASS — `FEATURE_049_CART_EDITED_WHILE_PAYING=PASS`; the later quantity edit survived confirmed-payment reconciliation while the unchanged item was removed. Exact checkout replay returned the original Order and Payment, Kafka hold, Order, and reconciliation converged. Secret values, tokens, Checkout URLs, provider payloads, and shopper identities were withheld. |
| Smoke runner budget hardening | PowerShell parser/static gate plus monotonic budget change in `feature-049-regular-purchase.ps1` | PASS — global and per-wait budgets now use `System.Diagnostics.Stopwatch`, so local NTP/clock adjustments cannot falsely expire an otherwise bounded smoke run. |

### Phase 4 — remaining concurrency/reconciliation test additions

| Test addition | Command / scope | Result |
|---|---|---|
| T059 reconciliation matrix additions | `CartReconciliationPersistenceIntegrationTests` now covers quantity edit, remove/re-add revision preservation, and same-order payload conflict in addition to apply/replay, partial no-op, and owner isolation. | COMPILED — the focused Maven run completed with BUILD SUCCESS, but the six Testcontainers-backed Cart integration cases were skipped because the local Docker Engine returned status 500 through the named pipe. Live execution remains required. |
| T061 overlapping multi-item hold test | `MultiItemRegularHoldConcurrencyTests` adds two concurrent two-line requests and asserts one 201/one 409, exactly one hold, two hold items, and no partial winner. | COMPILED — the focused Maven run completed with BUILD SUCCESS, but the Testcontainers-backed case was skipped for the same unavailable Docker Engine. Live execution remains required. |
| Cart/Inventory focused non-container regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/inventory-service -am "-Dtest=CartCheckoutRevisionTests,CartCheckoutSnapshotTests,ReconcilePurchasedCartSnapshotAvroMapperTests,CartArchitectureTests,RegularStockHoldApplicationServiceTest,RegularHoldCommandProcessingServiceTest,RegularStockHoldTest,InventoryRegularHoldKafkaConsumerConfigurationTests,InventoryRegularHoldSecurityConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 26 selected unit/architecture tests passed with zero failures/errors. |
| Current Cart/Order module verification after test additions | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/order-service -am verify` | BLOCKED — Cart completed (50 tests, 0 failures, 11 Docker-dependent skips), but Order stopped with 11 Testcontainers errors because Docker Desktop Linux Engine returned HTTP 500. This is an environment blocker, not a code assertion failure. |
| Current Cart/Order module verification (Docker restored) | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/order-service -am verify` | PASS — Maven reactor completed with `BUILD SUCCESS` in 06:03. Cart and Order completed with zero failures/errors; Cart integration suites and Order PostgreSQL/Testcontainers suites executed successfully. Order reported 183 tests, 8 existing prerequisite skips. |
| T076 Inventory recovery-boundary tests | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularHoldRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 5 tests passed with zero failures/errors. The suite covers inclusive expiry deadline, confirm-versus-release single-terminal-transition behavior, late-confirm current-state handling, equivalent create replay, and exactly-once movement/no-movement invariants. |

### Phase 5 — Inventory release, expiry, and recoverable publication (T081–T083)

| Task / gate | Command / scope | Result |
|---|---|---|
| T081–T083 Inventory implementation compile | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am -DskipTests compile` | PASS — Inventory compiles with idempotent release/current-state handling, durable expiry facts, release command processing, `SKIP LOCKED` due-hold selection, and disabled-by-default expiry scheduling. |
| T081–T083 focused verification | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=ReleaseRegularHoldAvroMapperTest,RegularHoldOutcomeAvroMapperTest,RegularHoldRecoveryIntegrationTests,InventoryRegularHoldKafkaConsumerConfigurationTests,RegularStockHoldApplicationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 17 selected tests passed with zero failures/errors. Release command identity/policy validation, release/expiry application transitions, released Avro outcome mapping, recovery boundaries, and Kafka consumer configuration are covered. |
| T081–T083 Inventory regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularStockHoldCompatibilityTests,MultiItemRegularHoldConcurrencyTests,RegularHoldRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 12 selected tests passed with zero failures/errors; Testcontainers PostgreSQL ran successfully. |
| Inventory full module regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am test` | PASS — 66 Inventory tests plus common-web/Avro prerequisites completed with zero failures/errors; Testcontainers PostgreSQL ran successfully. |
| T081–T083 repository hygiene | `git diff --check` | PASS — no whitespace errors. Existing line-ending normalization warnings are unchanged. |

### Phase 6 — Order regular-hold recovery contract (T077)

| Task / gate | Command / scope | Result |
|---|---|---|
| T077 focused recovery suite | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularHoldRecoverySagaTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 6 tests passed with zero failures/errors. The suite covers Payment failure release, deadline expiry, stale/reordered payment versions, higher-version late success, release-result/manual-review race, and same-version manual-review rejection. |
| T077 Order recovery regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularHoldRecoverySagaTests,RegularHoldPaidSagaTests,PurchaseSagaDomainTests,PurchaseSagaLateSuccessTests,PaymentFailureTransitionIntegrationTests,LatePaymentCorrectionIntegrationTests,PaymentSuccessTransitionIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 24 tests passed with zero failures/errors; PostgreSQL Testcontainers executed for the integration suites. |
| T084 domain prerequisite | `PurchaseSaga.java` regular-hold transitions | PARTIAL — regular `RELEASING_STOCK`, late success from an in-flight release, regular release completion, and manual-review recovery are now represented. Adapter routing and released/expired Kafka consumption remain T084–T086 work. |

### Phase 6 safety boundary

- T077 adds tests and the minimal domain transition surface needed to express the approved regular-hold recovery contract; it does not enable runtime flags or change Kubernetes/cloud state.
- Flash Sale reservation transitions remain on their existing `*_RESERVATION` states; regular Inventory holds use `*_STOCK` states so participant identity cannot be mixed.
- T084 is intentionally not marked complete: persistence routing, V2 release outbox facts, and released/expired result consumption remain pending tasks.

### Phase 7 — Regular intake crash-window coverage (T078 partial)

| Task / gate | Command / scope | Result |
|---|---|---|
| T078 checkpoint recovery tests | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 3 tests passed with zero failures/errors. A failed Product call leaves `RECEIVED`; an ambiguous Inventory hold resumes from `PRODUCT_VALIDATED` with the same proposed hold ID; a crash after hold acquisition and before Order acceptance resumes from `HOLD_ACQUIRED` without another Inventory call. |
| T078/T087 lease/concurrency boundary | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseRecoveryJobTests,RegularPurchasePersistenceIntegrationTests,RegularPurchaseMigrationIntegrationTests,RegularPurchaseRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 12 tests passed with zero failures/errors. The scheduled worker reconstructs the persisted Buy Now/Cart command with the original idempotency and item revisions; PostgreSQL `FOR UPDATE SKIP LOCKED` plus a persisted lease prevents another worker from reclaiming the same request until release or lease expiry. The existing 100-replay load test covers concurrent same-key semantics. |

### Phase 7 safety boundary

- The new tests use a deterministic in-memory persistence double and never mutate Docker, Kubernetes, databases, Kafka, or Secrets.
- The recovery worker is disabled by default; durable checkpoint semantics and lease ownership are proven without enabling runtime flags or changing any external environment.

### Phase 4 safety boundary and remaining evidence

- Cart checkout uses a captured Cart snapshot and monotonic Cart/item revisions. Order validates owner, exact line identity, quantity, price, and currency before one all-or-nothing Inventory hold.
- Cart reconciliation is conditional and idempotent: it removes only unchanged purchased lines, preserves edits, records an inbox receipt, and publishes no browser-visible secret or provider payload.
- The local smoke runner includes `CartPaid` and `CartEditedWhilePaying`; both require an operator to complete the hosted Stripe test Checkout. Both interactive scenarios are now live PASS.
- T059/T061 now have the required test coverage in source and the live Cart checkout gate covers the Kafka/reconciliation path. The negative T074 scenarios (`PriceChanged`, `InsufficientStock`), `CartPaid`, and `CartEditedWhilePaying` are PASS; T074/T075 evidence is complete.
- T076 recovery-boundary tests plus T081–T083 implementation and focused verification are PASS. Release command consumption, outbox outcome publication, and expiry scheduling remain disabled by default; no runtime flag, broker, database, Kubernetes, or cloud state was enabled or changed.

### Phase 8 — Kafka malformed/identity-conflict/retry/DLT coverage (T079)

| Task / gate | Command / scope | Result |
|---|---|---|
| Inventory regular-hold command failures | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularHoldKafkaFailureTests,InventoryRegularHoldKafkaConsumerConfigurationTests,ReleaseRegularHoldAvroMapperTest,ConfirmRegularHoldAvroMapperTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 9 selected tests passed with zero failures/errors. Malformed envelopes, unsupported record types, processor bypass, bounded retry delays, and command DLT routing are covered. |
| Order regular-hold result failures | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularHoldResultKafkaFailureTests,RegularStockHoldConfirmedAvroMapperTests,OrderRegularHoldResultsConsumerConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 7 selected tests passed with zero failures/errors. Unsupported records and identity mismatches remain unacknowledged; Saga identity conflicts are routed through the configured bounded retry/DLT handler. |
| Cart reconciliation failures | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=ReconcilePurchasedCartSnapshotAvroMapperTests,CartReconciliationKafkaFailureTests,CartReconciliationConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 7 selected tests passed with zero failures/errors. Missing/malformed records are permanent contract failures, Cart persistence failures remain retryable/unacknowledged, and successful processing acknowledges only after the use case returns; the configured Cart DLT route is exercised. |
| Cart reconciliation Kafka-key contract | Same focused Cart/Order suites plus mapper/outbox source review | PASS — the command now uses `cartId` as the Kafka key as specified; the Cart boundary validates that key before application mutation, while Order outbox construction and publishing preserve it. |
| Repository hygiene | `git diff --check` | PASS — no whitespace errors. |

### Phase 8 safety boundary

- Permanent malformed, unsupported, identity-conflict, and impossible-state records are classified for consumer-specific DLT handling; transient application/broker failures remain retryable and are not acknowledged early.
- Cart reconciliation now converts null/invalid Avro data into the typed `CartReconciliationRecordException` rather than an accidental null-pointer path.
- No Kafka topic, Schema Registry subject, database, Kubernetes resource, runtime flag, or Secret was changed by this validation. T080 load tests and T084–T087 recovery implementation remain pending.

### Phase 9 — Duplicate replay and final-unit load safety (T080)

| Test / gate | Command / scope | Result |
|---|---|---|
| Order duplicate Buy Now replay load | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseReplayLoadTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 1 test issued 100 concurrent identical Buy Now calls; one semantic acceptance was persisted and the other 99 calls replayed it without additional Product/Inventory/acceptance calls. |
| Inventory final-unit duplicate hold load | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=RegularHoldLoadTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 1 Testcontainers-backed test issued 100 concurrent identical holds for one final unit; exactly one 201 was returned, the remaining responses were idempotent 200 replays, exactly one hold/item was stored, and on-hand quantity was not oversold. |
| Idempotency ordering fix | `RegularStockHoldApplicationService.create` | PASS — duplicate `purchaseRequestId` is rechecked immediately after acquiring the deterministic inventory-item row lock and before availability calculation, preventing waiting replays from being misclassified as `INSUFFICIENT_STOCK`. |

### Phase 9 safety boundary

- The load tests are bounded to 100 identical requests and one final unit; they prove replay/oversell behavior without introducing a production traffic generator or changing runtime flags.
- The Inventory fix preserves the existing fingerprint conflict rule: a same-key request with different payload still fails instead of replaying.
- Testcontainers used an ephemeral PostgreSQL instance; no Kubernetes, cloud, Kafka topic, Schema Registry subject, or Secret was changed.
- T078 lease/concurrency recovery and T084–T087 runtime recovery implementation remain pending.

### Phase 10 — Order Saga regular expiry state (T084)

| Test / gate | Command / scope | Result |
|---|---|---|
| T084 Saga recovery suite | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularHoldRecoverySagaTests,RegularHoldPaidSagaTests,PurchaseSagaDomainTests,PurchaseSagaLateSuccessTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 19 tests passed with zero failures/errors. Regular release keeps the requested `CANCELLED`/`EXPIRED` terminal intent; an Inventory expiry fact closes a pending or in-flight release as `EXPIRED`; versions advance exactly once; late success remains on the manual-review boundary. |
| T084 domain change | `PurchaseSaga.completeRegularStockExpiry` and regular participant validation | PASS — regular stock expiry is explicit and cannot affect the Flash Sale reservation branch; invalid release terminal intent is rejected before persistence. |

### Phase 10 safety boundary

- This step changes only the framework-free Order Saga domain and its focused tests; it does not add Kafka consumers, persistence transitions, runtime flags, or cloud state.
- Released/expired Kafka mapping, atomic Order/Saga/outbox persistence, and consumer retry/DLT remain the next T085–T086 tasks.

### Phase 11 — Order recovery persistence and Kafka result boundary (T085–T086)

| Test / gate | Command / scope | Result |
|---|---|---|
| T085 recovery persistence integration | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularHoldRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 4 Testcontainers-backed tests prove atomic PaymentFailed-to-release routing, released and expired terminalization, inbox/outbox replay idempotency, late-success manual review, and rejection of a released fact whose causation does not match the active release command. |
| T086 strict result consumer/DLT boundary | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularHoldResultKafkaFailureTests,OrderRegularHoldResultsConsumerConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 6 selected tests prove released SpecificRecord mapping, acknowledgement only after the use case returns, conflict non-acknowledgement, malformed/unsupported record rejection, and non-retryable DLT classification. |
| T085/T086 regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=PaymentFailureTransitionIntegrationTests,LatePaymentCorrectionIntegrationTests,RegularHoldConfirmationIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 7 PostgreSQL-backed existing recovery/confirmation tests remain green after adding regular release/expiry wiring. |
| Order compile and hygiene | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am -DskipTests compile` and `git diff --check` | PASS — Order compiles successfully; no whitespace errors. |

### Phase 11 safety boundary

- Order writes the Saga state, Order terminal state, inbox receipt, and V2 terminal/manual-review outbox fact in one local transaction; duplicate facts replay without a second terminal effect.
- `RELEASED` facts must match the active release command while release is in flight; `EXPIRED` facts must use the stable hold-creation request as causation. Late success remains on the existing manual-review boundary.
- Result records are acknowledged only after local application success. Malformed records and identity/version conflicts are classified as non-retryable and routed to the Order-owned DLT; transient failures remain retryable.
- No runtime flag, Kafka topic, Schema Registry subject, Kubernetes resource, cloud state, or Secret was changed by this phase. The lease worker is recorded in Phase 12; T091 onward remains pending.

### Phase 12 — Leased regular-intake recovery worker (T078/T087)

| Test / gate | Command / scope | Result |
|---|---|---|
| Recovery migration | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseMigrationIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 3 tests passed; Liquibase applied changesets 001–006, including the recovery lease columns, constraints, and partial stale-row index. |
| Recovery worker/use-case reconstruction | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseRecoveryJobTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 3 tests passed; Buy Now and Cart commands preserve shopper, idempotency, cart/item revisions, trace identity, and release the lease after normal completion while downstream failure leaves it for retry. |
| PostgreSQL lease claim/release | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchasePersistenceIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 3 Testcontainers-backed tests passed; stale rows are claimed in bounded batches, a second worker cannot reclaim the same leased request, and release makes it claimable again. |
| Focused recovery suite | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=RegularPurchaseRecoveryJobTests,RegularPurchasePersistenceIntegrationTests,RegularPurchaseMigrationIntegrationTests,RegularPurchaseRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 12 tests passed with zero failures/errors. |
| Full Order module regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am test` | PASS — 209 Order tests plus 9 common-web and 25 Avro prerequisite tests completed with zero failures/errors; 8 existing prerequisite skips remained. |
| Repository hygiene | `git diff --check` | PASS — no whitespace errors; only existing line-ending normalization warnings are reported. |

### Phase 12 safety boundary

- Migration 006 adds only operational recovery metadata; it does not delete or rewrite business data.
- The lease is short-lived and persisted in PostgreSQL. The database lock is held only during claim; Product/Cart/Inventory calls execute outside that transaction and retry after lease expiry.
- Recovery reuses the same checkout use case and persisted idempotency/identity values, so replay cannot create a second semantic Order/hold effect.
- `ORDER_REGULAR_PURCHASE_RECOVERY_ENABLED` remains false by default. No Kubernetes, cloud, Kafka, Schema Registry, Secret, or runtime state was changed by these tests.

### Phase 13 — Payment deadline and provider reconciliation boundary (T088)

| Task / gate | Command / scope | Result |
|---|---|---|
| T088 domain deadline/reconciliation tests | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/payment-service -am "-Dtest=PaymentTests,CheckoutRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 9 tests passed with zero failures/errors. A new Checkout attempt at the exact deadline is rejected and transitions the Payment to `EXPIRED` without allocating an attempt; a verified provider success after local expiry still converges the existing attempt to `SUCCEEDED` with the original Checkout Session and PaymentIntent identity. |
| T088 persistence recovery identity | Same focused suite | PASS — PostgreSQL-backed recovery proof retains the expired Payment, provider Session, provider idempotency key, and pending refresh work so provider reconciliation can still use the durable identity; no new Checkout attempt or duplicate recovery identity is created. |
| Payment module regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/payment-service -am test` | PASS — Payment module 150 tests plus 9 common-web and 25 Avro prerequisite tests completed with zero failures/errors; 6 existing Kafka-dependent skips remained. |
| Repository hygiene | `git diff --check` | PASS — no whitespace errors. |

### Phase 13 safety boundary

- T088 changes tests only. Payment production contracts, database schema, deadline duration, provider reconciliation policy, Kafka topics, runtime flags, Kubernetes resources, and Secrets are unchanged.
- The proof distinguishes local intake policy from provider truth: the deadline blocks a new Checkout allocation, while a verified late provider success may still converge the already-created provider attempt. No refund or destructive row deletion is introduced.

### Phase 14 — Order observability and trace continuity (T089)

| Task / gate | Command / scope | Result |
|---|---|---|
| T089 focused observability/recovery tests | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am "-Dtest=OrderObservabilityTests,RegularPurchaseRecoveryJobTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 8 tests passed with zero failures/errors. Regular Buy Now/Cart intake, recovery checkpoints, Saga transitions, and manual-review counters use bounded vocabularies; unknown or identifier-like values collapse to `other` and no business IDs are metric labels. W3C trace context remains scoped through the existing request filter and regular-intake Observation. |
| T089 Order regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service -am test` | PASS — 210 Order tests plus 9 common-web and 25 Avro prerequisite tests completed with zero failures/errors; 8 existing prerequisite skips remained. |
| T089 repository hygiene | `git diff --check` | PASS — no whitespace errors; only existing line-ending normalization warnings are reported. |

### Phase 14 safety boundary

- Metrics add only bounded operation/counter dimensions (`source`, `outcome`, `stage`, Saga state, and fixed manual-review reason); request IDs, shopper IDs, Cart IDs, tokens, provider payloads, and raw trace values are never metric labels.
- Regular intake and recovery observations are no-op compatible for existing unit-test constructors and remain disabled with the regular-purchase runtime flags unless explicitly enabled.
- No database migration, Kafka topic/schema, Kubernetes resource, Secret, or cloud state was changed by T089 validation.

### Phase 15 — Inventory hold observability (T090)

| Task / gate | Command / scope | Result |
|---|---|---|
| T090 bounded metric facade | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am "-Dtest=InventoryObservabilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 2 focused tests passed with zero failures/errors. Hold lifecycle, outbox backlog/age, DLT, and expiry metrics accept only fixed vocabularies; unknown, identifier-like, and free-form reason values collapse to `other`. |
| T090 Inventory regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/inventory-service -am test` | PASS — 77 Inventory tests plus the common-web and Avro prerequisite suites completed with zero failures/errors (one existing smoke test skipped). |
| T090 repository hygiene | `git diff --check` | PASS — no whitespace errors; only existing line-ending normalization warnings are reported. |

### Phase 15 safety boundary

- The new `InventoryObservability` facade uses Micrometer's registry abstraction and is safe for Actuator/Prometheus wiring; it does not construct a Prometheus registry or introduce a production dependency.
- Metric labels are bounded to hold lifecycle state, DLT boundary, and expiry outcome vocabularies. Hold IDs, order IDs, variant IDs, shopper IDs, trace values, raw reasons, and payloads are never labels.
- This task adds no database migration, Kafka topic/schema, Kubernetes resource, runtime flag, Secret, or cloud mutation. T091 Cart reconciliation observability is covered in Phase 16.

### Phase 16 — Cart reconciliation observability (T091)

| Task / gate | Command / scope | Result |
|---|---|---|
| T091 bounded metric facade | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartObservabilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 2 focused tests passed; reconciliation outcomes are limited to `applied`, `partial_noop`, `replayed`, `conflict`, and `other`, while DLT publication has no business-identifier labels. |
| T091 Cart reconciliation integration regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartObservabilityTests,CartReconciliationConfigurationTests,CartReconciliationKafkaFailureTests,CartReconciliationPersistenceIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS — 13 selected tests passed with zero failures/errors, including PostgreSQL reconciliation behavior and recoverer wiring. |
| T091 Cart module regression | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am test` | PASS — 57 Cart tests plus 9 common-web and 25 Avro prerequisite tests completed with zero failures/errors. |
| T091 repository hygiene | `git diff --check` | PASS — no whitespace errors; only existing line-ending normalization warnings are reported. |

### Phase 16 safety boundary

- Cart reconciliation metrics are emitted after the local inbox/conditional-cleanup result and from the configured DLT recoverer; transient processing failures remain retryable and are not acknowledged early.
- No Cart migration, topic/schema, runtime flag, Secret, Kubernetes resource, or cloud state was changed. T092 onward remains pending.

### Phase 17 — Scenario aggregate, API registry, and operational observability (T092/T093/T095–T099)

| Task / gate | Command / scope | Result |
|---|---|---|
| T092 deterministic recovery scenarios | `pwsh -NoLogo -NoProfile -File infra/docker/smoke/feature-049-regular-purchase.ps1 -Scenario PaymentFailed/HoldExpired/Replay/Concurrency/LateSuccess -TimeoutSeconds 1800` | PASS — each bounded scenario completed with sanitized diagnostics. The explicit DependencyRestart scenario remains deferred because it restarts the local Order container and requires `-AllowDependencyRestart`. |
| T093 Flash Sale regression contracts | `pwsh -NoLogo -NoProfile -File infra/docker/smoke/feature-044-purchase-saga.ps1 -Scenario Contracts -TimeoutSeconds 1800`; `-Scenario Replay`; `-Scenario LateSuccess` | PASS — Feature 044 contracts/topic/schema, replay/idempotency, and late-success gates passed. Phase 22/24 cloud Stripe regression remains pending an explicit cloud run. |
| T095 API registry/OpenAPI wiring | `pwsh -NoLogo -NoProfile -File infra/scripts/docs/verify-api-documentation.ps1` | PASS — 47 endpoint rows (39 Gateway-public, 7 internal, 1 JWKS), eight service documents, safe documentation defaults, and no public `/internal/**` Gateway route. |
| T096 monitoring rules/dashboard | JSON parse of `infra/monitoring/grafana/dashboards/seckill-overview.json`; Kustomize config review | PASS — regular intake/recovery, hold expiry/outbox/DLT, and Cart reconciliation panels/rules use bounded metric names and no business identifiers. |
| T097 runbook | `docs/runbooks/regular-purchase-checkout.md` source review | PASS — rejection, hold expiry, replay/DLT, orphan-hold, manual-review, dependency-restart, rollback, and Secret-redaction procedures are documented. |
| T098 audit | `rg` audit over Order/Inventory/Cart comments, logs, errors, metrics, and trace code | PASS — no raw credentials/provider payloads or unbounded metric labels were added; error/log paths use safe categories and trace boundaries. |
| T099 aggregate runner | `pwsh -NoLogo -NoProfile -File infra/docker/smoke/feature-049-regular-purchase.ps1 -Scenario All -TimeoutSeconds 1800` | PASS — static, PaymentFailed, HoldExpired, Replay, Concurrency, LateSuccess, and Feature 044 Replay all passed. Interactive Docker/Stripe and dependency restart are explicitly deferred by default. |

### Phase 17 safety boundary

- The aggregate runner is deterministic by default. Docker Checkout/browser scenarios and service
  restarts require explicit switches so CI cannot mutate a shared local stack accidentally.
- Registry and monitoring changes are documentation/observability-only; no business rows, Secrets,
  cloud resources, or public regular-purchase intake flags were enabled by this phase.
- T092 remains open only for the explicit local dependency restart. T093/T094 and cloud tasks remain
  open until the user authorizes the EKS/Stripe gates.

### Phase 18 — Full local reactor and release prerequisites (T100–T101)

| Task / gate | Command / scope | Result |
|---|---|---|
| T100 affected module verification | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/authentication-service,services/product-service,services/cart-service,services/inventory-service,services/order-service,services/payment-service,services/api-gateway,contracts/kafka-avro-contracts -am test` | PASS — Authentication, Product, Cart, Inventory, Order, Payment, Gateway, common-web, and Avro contract prerequisites completed with exit code 0; no test failures or errors were reported. |
| T101 full clean reactor | `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` | PASS — the complete 13-module reactor (including Notification and Inventory) completed with exit code 0 in 15:52; every module reported BUILD SUCCESS, with existing Kafka-dependent skips only. |
| T100/T101 hygiene | `git diff --check` | PASS — no whitespace errors; existing line-ending normalization warnings are unrelated to this feature. |

### Phase 18 safety boundary

- The full verification used Testcontainers and local test fixtures only; it did not apply Kubernetes resources, run cloud migrations, publish ECR images, change Kafka/Schema Registry state, or alter Secrets.
- Warnings from Spring schedulers during Testcontainers shutdown are teardown noise; every module and the reactor exited successfully with zero failures/errors.
- T102 local infrastructure validation is next. T103–T105 are source-only release/rehearsal work. T106 onward requires an explicit cloud gate after the local checks pass.

### Phase 19 — Infrastructure safety and release scripts (T102–T105)

| Task / gate | Command / scope | Result |
|---|---|---|
| T102 PowerShell/static checks | PowerShell AST parse for `feature-049-regular-purchase.ps1`, `verify-api-documentation.ps1`, `phase49-regular-purchase-migration-gate.ps1`, and `phase49-regular-purchase-rollback-rehearsal.ps1`; `git diff --check` | PASS — all scripts parse; repository diff has no whitespace errors. |
| T102 Compose render | `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config` | PASS — Compose rendered without printing the environment or Secret values. |
| T102 Kustomize/topic contract validation | `kubectl kustomize infra/k8s/overlays/cloud`; `kubectl kustomize infra/k8s/overlays/cloud-migrations/feature-049`; Feature 049 Static scenario | PASS — cloud and three-Job migration manifests render; the validation-only local contract gate passes. Kubernetes client discovery dry-run is blocked by the current EKS endpoint DNS failure and remains unmarked until the cluster endpoint resolves. |
| T103 migration gate | PowerShell parse plus source review of `infra/scripts/gitops/phase49-regular-purchase-migration-gate.ps1` | PASS — exact remote `develop` SHA/ECR tag resolution, Cart → Inventory → Order sequencing, no-rerun guard, validation-only default, explicit `-Apply`, bounded timeout, and Secret-redacted diagnostics are implemented. No Job was created. |
| T104 rollback rehearsal | PowerShell parse plus source review of `infra/scripts/gitops/phase49-regular-purchase-rollback-rehearsal.ps1` | PASS — prior Cart/Inventory/Order image and enum checks, expanded-schema checks, aggregate-only state reads, and mutation-free stop behavior are implemented. No image, row, or Kubernetes state was changed. |
| T105 Cart delivery ownership | `kubectl kustomize infra/k8s/overlays/cloud`; `kubectl kustomize infra/k8s/overlays/cloud-migrations/feature-049`; Terraform format; workflow YAML parse | PASS — Cart now has ECR Terraform ownership, base Deployment/ClusterIP Service, cloud ConfigMap/Secret wiring, immutable image slot, and selective GitHub delivery mapping. Unrelated Payment is not selected for a Cart-only change. |
| Cart platform ownership regression | `kubectl kustomize infra/k8s/overlays/cloud-migrations`; `pwsh -NoLogo -NoProfile -File infra/scripts/gitops/tests/phase25-seckill-observability.tests.ps1`; PowerShell AST parse of Phase 16/17/19/21/22/25 scripts | PASS — the nine-service cloud/monitoring ownership lists include Cart, the initial Cart migration Job renders, and Prometheus has a private Cart target without exposing it publicly. |

### Phase 19 safety boundary

- T102 is intentionally not checked yet because the exact `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` command cannot download the EKS OpenAPI endpoint (`lookup ...eks.amazonaws.com: no such host`). The pure Kustomize render passes; rerun the client dry-run after the EKS DNS/network path is restored.
- T103/T104 are safe-by-default scripts. Only T103 `-Apply` creates migration Jobs, and T104 never performs a rollback. T106–T112 remain cloud-gated and require the reviewed images, active EKS endpoint, and operator authorization.
