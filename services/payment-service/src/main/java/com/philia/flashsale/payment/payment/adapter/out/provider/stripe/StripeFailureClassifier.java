package com.philia.flashsale.payment.payment.adapter.out.provider.stripe;

import com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;

/** Converts Stripe's exception hierarchy to a small, non-sensitive policy vocabulary. */
public final class StripeFailureClassifier {

    public ProviderFailureCategory classify(Throwable throwable) {
        if (throwable instanceof ApiConnectionException) {
            return ProviderFailureCategory.TIMEOUT;
        }
        if (throwable instanceof AuthenticationException) {
            return ProviderFailureCategory.AUTHENTICATION;
        }
        if (throwable instanceof RateLimitException) {
            return ProviderFailureCategory.RATE_LIMITED;
        }
        if (throwable instanceof InvalidRequestException invalid) {
            Integer status = invalid.getStatusCode();
            return status != null && status == 404
                    ? ProviderFailureCategory.NOT_FOUND : ProviderFailureCategory.INVALID_REQUEST;
        }
        if (throwable instanceof ApiException api) {
            Integer status = api.getStatusCode();
            if (status != null && status == 404) {
                return ProviderFailureCategory.NOT_FOUND;
            }
            if (status != null && status >= 500) {
                return ProviderFailureCategory.TRANSIENT;
            }
            return ProviderFailureCategory.UNKNOWN;
        }
        if (throwable instanceof StripeException stripe) {
            Integer status = stripe.getStatusCode();
            if (status != null && status == 404) {
                return ProviderFailureCategory.NOT_FOUND;
            }
            if (status != null && status >= 500) {
                return ProviderFailureCategory.TRANSIENT;
            }
        }
        return ProviderFailureCategory.UNKNOWN;
    }
}
