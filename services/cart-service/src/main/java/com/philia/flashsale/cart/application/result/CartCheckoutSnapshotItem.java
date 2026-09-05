package com.philia.flashsale.cart.application.result;

import java.util.UUID;

/** Cart-owned immutable item used by Order before acceptance; it contains no Product price data. */
public record CartCheckoutSnapshotItem(UUID variantId, int quantity, long itemVersion) { }
