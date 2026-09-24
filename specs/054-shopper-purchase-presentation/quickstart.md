# Validation Quickstart: Shopper Purchase Presentation

This guide describes validation after implementation. It does not authorize a cloud deployment or database mutation outside the approved local test environment.

## Prerequisites

- Java 21, Maven wrapper dependencies, Node dependencies for `flash-sale frontend/QuickCart`.
- Local PostgreSQL/Testcontainers runtime for Order persistence tests.
- Active feature pointer: `.specify/feature.json` targets `specs/054-shopper-purchase-presentation`.

## Automated checks

From the repository root:

```powershell
.\mvnw.cmd -pl services/order-service -am verify
npm.cmd --prefix 'flash-sale frontend/QuickCart' run build
npm.cmd --prefix 'flash-sale frontend/QuickCart' run test:catalog
```

Required scenarios: new Buy Now/Cart lines keep names across recovery; a Flash Sale Order with Product unavailable is still created; duplicate PurchaseAccepted cannot rewrite a snapshot; old rows and JSON payloads remain readable; Order API preserves authorization/envelope/money while adding nullable names.

## Manual storefront review

In local development, open a product with multiple variants and confirm its card says `From` for the minimum displayable price, while product detail follows the selected variant. Confirm the universal Flash Sale badge is gone and the campaign-ID control is described as a manual/demo path. Open a named Order and an older unnamed Order; only the latter uses the neutral label and secondary variant code. Review pending, confirmed, cancelled/expired, payment and reservation states and an unknown-state fixture; none should falsely claim success.

## Migration and rollback rehearsal

Run the Order migration integration test against a fresh database and one at the previous schema version. The new columns are nullable and preserve existing rows. If the new Order image must be rolled back, restore the previous image without dropping the new columns or erasing name snapshots; record any operational rollout verification in `validation.md`.
