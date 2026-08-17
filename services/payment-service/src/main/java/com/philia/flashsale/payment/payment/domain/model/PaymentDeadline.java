package com.philia.flashsale.payment.payment.domain.model;

import com.philia.flashsale.payment.payment.domain.exception.InvalidPaymentException;
import java.time.Instant;

/** Immutable absolute deadline copied from the PaymentRequested command. */
public record PaymentDeadline(Instant value) {

    public PaymentDeadline {
        if (value == null) {
            throw new InvalidPaymentException("payment deadline must not be null");
        }
    }

    public boolean hasPassed(Instant now) {
        if (now == null) {
            throw new InvalidPaymentException("current time must not be null");
        }
        return !now.isBefore(value);
    }
}
