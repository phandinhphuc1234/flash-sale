import test from "node:test";
import assert from "node:assert/strict";
import {
  STOREFRONT_DESTINATIONS,
  classifyStorefrontRoute,
} from "../lib/storefrontNavigation.mjs";

test("Home is active only for the exact root route", () => {
  assert.equal(classifyStorefrontRoute("/"), STOREFRONT_DESTINATIONS.HOME);
  assert.equal(classifyStorefrontRoute("/account/profile"), null);
  assert.equal(classifyStorefrontRoute("/my-orders"), null);
});

test("Shop owns catalog and product-detail route families", () => {
  assert.equal(classifyStorefrontRoute("/all-products"), STOREFRONT_DESTINATIONS.SHOP);
  assert.equal(classifyStorefrontRoute("/all-products/electronics"), STOREFRONT_DESTINATIONS.SHOP);
  assert.equal(classifyStorefrontRoute("/product/product-123"), STOREFRONT_DESTINATIONS.SHOP);
});

test("Cart owns its exact route family", () => {
  assert.equal(classifyStorefrontRoute("/cart"), STOREFRONT_DESTINATIONS.CART);
  assert.equal(classifyStorefrontRoute("/cart/review"), STOREFRONT_DESTINATIONS.CART);
});

test("Flash Sale owns discovery and campaign detail routes", () => {
  assert.equal(classifyStorefrontRoute("/flash-sale"), STOREFRONT_DESTINATIONS.FLASH_SALE);
  assert.equal(classifyStorefrontRoute("/flash-sale/campaign-1"), STOREFRONT_DESTINATIONS.FLASH_SALE);
  assert.equal(classifyStorefrontRoute("/flash-sales"), null);
});

test("Route prefixes match complete path segments only", () => {
  assert.equal(classifyStorefrontRoute("/productivity"), null);
  assert.equal(classifyStorefrontRoute("/all-products-old"), null);
  assert.equal(classifyStorefrontRoute("/cartoon"), null);
});

test("Query strings, fragments, trailing slashes, and invalid values are safe", () => {
  assert.equal(classifyStorefrontRoute("/all-products?page=2"), STOREFRONT_DESTINATIONS.SHOP);
  assert.equal(classifyStorefrontRoute("/product/item-1#reviews"), STOREFRONT_DESTINATIONS.SHOP);
  assert.equal(classifyStorefrontRoute("/cart/"), STOREFRONT_DESTINATIONS.CART);
  assert.equal(classifyStorefrontRoute(null), null);
  assert.equal(classifyStorefrontRoute(""), null);
});

test("Account, purchase follow-up, seller, and unknown routes stay unselected", () => {
  for (const route of [
    "/orders/order-1",
    "/payments/success",
    "/reservations/reservation-1",
    "/seller",
    "/help",
  ]) {
    assert.equal(classifyStorefrontRoute(route), null, route);
  }
});
