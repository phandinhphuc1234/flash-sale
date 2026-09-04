package com.philia.flashsale.cart.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Exact trust boundary for Order's internal Cart checkout-snapshot capability. */
@ConfigurationProperties(prefix = "cart.security.order-internal-jwt")
public record CartInternalJwtProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        String subject,
        String requiredScope) { }
