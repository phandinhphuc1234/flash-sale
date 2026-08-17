package com.philia.flashsale.payment.payment.domain.model;

import java.util.UUID;

/** Typed identity for the Order snapshot owned by the Purchase Saga. */
public record OrderId(UUID value) {

    public OrderId {
        if (value == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }
}
