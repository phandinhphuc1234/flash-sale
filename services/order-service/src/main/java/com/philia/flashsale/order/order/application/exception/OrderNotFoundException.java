package com.philia.flashsale.order.order.application.exception;

import java.util.UUID;

/** Non-enumerating application outcome for an absent or foreign-owned Order. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
