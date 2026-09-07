package com.philia.flashsale.order.regularpurchase.domain.model;

/** Result of comparing an authenticated checkout retry with an existing intake identity. */
public enum IdempotencyMatch {
    NOT_MATCHED,
    REPLAY,
    CONFLICT
}
