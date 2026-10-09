# Quickstart: Validate Storefront Active Navigation

## Automated checks

From the repository root:

```powershell
node --test '.\flash-sale frontend\QuickCart\tests\storefrontNavigation.test.mjs'
node --test '.\flash-sale frontend\QuickCart\tests\catalogQuery.test.mjs' '.\flash-sale frontend\QuickCart\tests\purchasePresentation.test.mjs' '.\flash-sale frontend\QuickCart\tests\storefrontNavigation.test.mjs'
npm.cmd --prefix '.\flash-sale frontend\QuickCart' run build
git diff --check
```

## Browser review

1. Start the existing frontend normally.
2. Review widths 375, 768, 1024, and 1440 pixels.
3. Open `/`: Home is the only selected destination.
4. Open `/all-products`: Shop is selected.
5. Open an existing `/product/<id>`: Shop remains selected.
6. Open `/cart`: Cart is selected and its count remains readable.
7. Open `/my-orders`, `/account/profile`, or `/orders/<id>`: Home and Shop are not falsely selected.
8. Use Tab/Shift+Tab: every Navbar action has visible focus.
9. Inspect the active link: it exposes `aria-current="page"`.
10. Confirm no Flash Sale/Help link routes incorrectly to Home and no horizontal page overflow exists.

## Expected scope

- No backend request or response changes.
- No seller Navbar/sidebar changes.
- No new dependency.
