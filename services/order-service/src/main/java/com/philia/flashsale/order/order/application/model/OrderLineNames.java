package com.philia.flashsale.order.order.application.model;

/** Optional Product-owned labels captured for one Order line at creation time. */
public record OrderLineNames(String productName, String variantName) {
}
