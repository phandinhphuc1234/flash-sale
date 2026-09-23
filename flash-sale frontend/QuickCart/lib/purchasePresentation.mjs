const STATUS_COPY = {
  order: {
    PENDING_PAYMENT: { label: "Awaiting payment", guidance: "Complete payment before the payment window expires.", tone: "attention" },
    CONFIRMED: { label: "Order confirmed", guidance: "Your order has been confirmed.", tone: "success" },
    CANCELLED: { label: "Order cancelled", guidance: "This order is no longer active.", tone: "neutral" },
    EXPIRED: { label: "Order expired", guidance: "The payment window expired before the order was completed.", tone: "neutral" },
  },
  payment: {
    PENDING: { label: "Payment pending", guidance: "Payment is being prepared.", tone: "attention" },
    PROCESSING: { label: "Payment processing", guidance: "The payment provider is processing this payment.", tone: "attention" },
    UNKNOWN: { label: "Payment status unavailable", guidance: "Refresh shortly to check the payment status.", tone: "neutral" },
    SUCCEEDED: { label: "Payment successful", guidance: "Payment received successfully.", tone: "success" },
    FAILED: { label: "Payment failed", guidance: "The payment could not be completed. You can try again.", tone: "danger" },
    EXPIRED: { label: "Payment expired", guidance: "This payment session is no longer available.", tone: "neutral" },
  },
  reservation: {
    RESERVED: { label: "Reserved", guidance: "Your item is held while the order is created.", tone: "attention" },
    CONFIRMED: { label: "Reservation confirmed", guidance: "The reserved item is attached to your confirmed order.", tone: "success" },
    RELEASED: { label: "Reservation released", guidance: "The item was returned to available stock.", tone: "neutral" },
    EXPIRED: { label: "Reservation expired", guidance: "The reservation window ended before checkout completed.", tone: "neutral" },
  },
};

const UNKNOWN_STATUS = {
  label: "Status unavailable",
  guidance: "Refresh shortly to check the latest status.",
  tone: "neutral",
};

export function getStatusCopy(kind, status) {
  return STATUS_COPY[kind]?.[status] || UNKNOWN_STATUS;
}

export function getOrderSourceLabel(source) {
  if (source === "CART") return "Cart checkout";
  if (source === "BUY_NOW") return "Buy now";
  if (source === "FLASH_SALE") return "Flash Sale";
  return "Order checkout";
}

export function displayOrderItem(item = {}) {
  const productName = typeof item.productName === "string" ? item.productName.trim() : "";
  const variantName = typeof item.variantName === "string" ? item.variantName.trim() : "";
  const primary = [productName, variantName].filter(Boolean).join(" · ") || "Item from this order";
  const variantId = typeof item.variantId === "string" ? item.variantId : "";
  return {
    primary,
    secondary: productName || variantName ? variantId : (variantId ? `Variant ${variantId}` : "Product details unavailable"),
  };
}

export function selectDisplayPrice(variants = []) {
  const prices = variants
    .filter((variant) => variant?.basePrice !== null && variant?.basePrice !== undefined && variant?.basePrice !== "")
    .map((variant) => Number(variant.basePrice))
    .filter((price) => Number.isFinite(price) && price >= 0);
  if (!prices.length) return null;
  const amount = Math.min(...prices);
  return { amount, prefix: prices.length > 1 ? "From" : "" };
}

export function canStartPayment(status) {
  return !["SUCCEEDED", "EXPIRED"].includes(status);
}
