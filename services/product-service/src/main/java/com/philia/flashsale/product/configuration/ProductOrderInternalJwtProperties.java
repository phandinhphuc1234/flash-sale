package com.philia.flashsale.product.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Exact trust settings for Order's internal regular-purchase quote capability. */
@ConfigurationProperties(prefix = "flashsale.product.security.order-internal-jwt")
public record ProductOrderInternalJwtProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        String subject,
        String requiredScope) { }
