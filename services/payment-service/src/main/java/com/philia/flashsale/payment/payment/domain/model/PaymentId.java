package com.philia.flashsale.payment.payment.domain.model;

import java.util.UUID;

/** Typed identity for the Payment aggregate. */
public record PaymentId(UUID value) {

    public PaymentId {
        if (value == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
    }

    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }
}
