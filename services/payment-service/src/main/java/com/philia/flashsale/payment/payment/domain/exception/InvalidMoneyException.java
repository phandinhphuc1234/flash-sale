package com.philia.flashsale.payment.payment.domain.exception;

/** Raised when a monetary value cannot be represented by the Payment contract. */
public final class InvalidMoneyException extends PaymentDomainException {

    public InvalidMoneyException(String message) {
        super(message);
    }
}
