package com.philia.flashsale.payment.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit feature flag for the owner-facing Checkout capability. */
@ConfigurationProperties(prefix = "payment.checkout")
public record PaymentCheckoutProperties(boolean enabled) {
}
