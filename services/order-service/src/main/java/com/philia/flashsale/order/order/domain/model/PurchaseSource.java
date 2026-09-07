package com.philia.flashsale.order.order.domain.model;

/** Immutable origin of the accepted commercial intent; it is never inferred from nullable IDs. */
public enum PurchaseSource {
    FLASH_SALE,
    BUY_NOW,
    CART
}
