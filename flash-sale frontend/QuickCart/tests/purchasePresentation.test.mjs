import test from "node:test";
import assert from "node:assert/strict";
import {
  canStartPayment,
  displayOrderItem,
  getOrderSourceLabel,
  getStatusCopy,
  selectDisplayPrice,
} from "../lib/purchasePresentation.mjs";

test("known statuses have customer-facing labels and guidance", () => {
  assert.equal(getStatusCopy("order", "PENDING_PAYMENT").label, "Awaiting payment");
  assert.equal(getStatusCopy("payment", "SUCCEEDED").label, "Payment successful");
  assert.equal(getStatusCopy("reservation", "RESERVED").label, "Reserved");
});

test("unknown status is safe and neutral", () => {
  const result = getStatusCopy("order", "NEW_BACKEND_STATE");
  assert.equal(result.label, "Status unavailable");
  assert.equal(result.tone, "neutral");
});

test("order item uses snapshot names and keeps a safe legacy fallback", () => {
  assert.deepEqual(displayOrderItem({ productName: "Keyboard", variantName: "Blue", variantId: "v-1" }), {
    primary: "Keyboard · Blue",
    secondary: "v-1",
  });
  assert.deepEqual(displayOrderItem({ variantId: "v-legacy" }), {
    primary: "Item from this order",
    secondary: "Variant v-legacy",
  });
});

test("catalog price uses the lowest valid variant price", () => {
  assert.deepEqual(selectDisplayPrice([{ basePrice: 900 }, { basePrice: 700 }]), { amount: 700, prefix: "From" });
  assert.deepEqual(selectDisplayPrice([{ basePrice: 900 }]), { amount: 900, prefix: "" });
  assert.equal(selectDisplayPrice([{ basePrice: null }]), null);
});

test("payment retry boundary stays compatible", () => {
  assert.equal(canStartPayment("PENDING"), true);
  assert.equal(canStartPayment("FAILED"), true);
  assert.equal(canStartPayment("SUCCEEDED"), false);
  assert.equal(canStartPayment("EXPIRED"), false);
});

test("purchase sources use friendly labels", () => {
  assert.equal(getOrderSourceLabel("CART"), "Cart checkout");
  assert.equal(getOrderSourceLabel("BUY_NOW"), "Buy now");
  assert.equal(getOrderSourceLabel("FLASH_SALE"), "Flash Sale");
  assert.equal(getOrderSourceLabel("UNKNOWN"), "Order checkout");
});
