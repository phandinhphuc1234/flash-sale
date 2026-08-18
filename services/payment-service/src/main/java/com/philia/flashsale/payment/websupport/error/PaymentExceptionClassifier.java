package com.philia.flashsale.payment.websupport.error;

import com.philia.flashsale.payment.payment.application.exception.HostedCheckoutProviderException;
import com.philia.flashsale.payment.payment.application.exception.PaymentCheckoutException;
import com.philia.flashsale.payment.payment.application.exception.PaymentNotFoundException;

/** Converts application/provider failures into stable client-safe codes. */
public final class PaymentExceptionClassifier {
    private PaymentExceptionClassifier() { }

    public static PaymentErrorCode classify(Throwable throwable) {
        if (throwable instanceof PaymentNotFoundException) {
            return PaymentErrorCode.PAYMENT_NOT_FOUND;
        }
        if (throwable instanceof PaymentAuthenticationException) {
            return PaymentErrorCode.AUTHENTICATION_REQUIRED;
        }
        if (throwable instanceof PaymentCheckoutException exception) {
            return switch (exception.outcome()) {
                case NOT_FOUND -> PaymentErrorCode.PAYMENT_NOT_FOUND;
                case IDEMPOTENCY_CONFLICT -> PaymentErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT;
                case NOT_PAYABLE -> PaymentErrorCode.PAYMENT_NOT_PAYABLE;
                case DEADLINE_PASSED -> PaymentErrorCode.PAYMENT_DEADLINE_PASSED;
                case ATTEMPT_LIMIT_REACHED -> PaymentErrorCode.PAYMENT_ATTEMPT_LIMIT_REACHED;
                case CHECKOUT_IN_PROGRESS -> PaymentErrorCode.PAYMENT_CHECKOUT_IN_PROGRESS;
                case PROVIDER_UNAVAILABLE -> PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;
                case INVALID_IDEMPOTENCY_KEY -> PaymentErrorCode.VALIDATION_FAILED;
            };
        }
        if (throwable instanceof HostedCheckoutProviderException) {
            return PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE;
        }
        if (throwable instanceof IllegalArgumentException) {
            return PaymentErrorCode.VALIDATION_FAILED;
        }
        return PaymentErrorCode.PAYMENT_INTERNAL_ERROR;
    }
}
