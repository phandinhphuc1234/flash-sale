# Validation: Shopper Purchase Presentation

Date: 2026-09-24

## Passed gates

| Scope | Command | Result |
| --- | --- | --- |
| Order compile | `./mvnw.cmd -pl services/order-service -am -DskipTests compile` | PASS |
| Focused Order tests | `./mvnw.cmd -pl services/order-service -am "-Dtest=RegularPurchaseRequestDomainTests,RegularPurchaseCheckoutServiceTests,CreateOrderFromAcceptedPurchaseServiceTests,OrderLineNameLookupAdapterTests,OrderQueryControllerTests,AcceptedPurchasePersistenceIntegrationTests,OwnedOrderQueryPersistenceIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS; 28 tests, 0 failures/errors |
| Order module verify | `./mvnw.cmd -pl services/order-service -am verify` | PASS; 233 tests, 0 failures/errors, 8 skipped |
| Frontend helper tests | `node --test tests/purchasePresentation.test.mjs` from `flash-sale frontend/QuickCart` | PASS; 6 tests |
| Frontend production build | `npm.cmd run build` from `flash-sale frontend/QuickCart` | PASS; Next.js production build completed |
| Frontend dev runtime recovery | Reset generated `flash-sale frontend/QuickCart/.next`, restarted `npm.cmd run dev`, then requested `http://localhost:3000/` | PASS; HTTP 200 and Ecommerce Flash Sale home rendered |
| Repository whitespace | `git diff --check` | PASS |

## What the gates cover

- Regular-purchase quote names are captured in the durable snapshot without changing the idempotency fingerprint.
- Flash Sale name lookup is best effort; missing or failed lookup leaves a truthful nullable snapshot and replay does not overwrite persisted names.
- The Order line migration is additive and nullable; migration tests apply all 7 Order changesets and verify legacy rows remain readable.
- Owner-only Order responses expose nullable `productName` and `variantName` while retaining existing money, quantity, and status fields.
- Frontend Order, Payment, Reservation, product-card, and product-detail copy uses canonical backend state and neutral fallbacks.

## Limitations and deferred gates

- `PurchaseAcceptedConsumerIntegrationTests` and other Kafka-dependent tests are opt-in and skipped when no live broker is available; the module verify result records this as 8 skipped tests.
- T024 full desktop/mobile review remains deferred; after resetting the generated cache, the homepage was verified at HTTP 200 in the local browser. Order, Payment/Reservation and responsive-width review still need a separate pass.
- T025 full-repository `./mvnw.cmd clean verify` was not run; only the affected Order reactor plus the frontend build and helper tests were run.
- No Kubernetes apply, Kafka topic/schema change, stock/payment behavior change, or cloud rollout was performed.
