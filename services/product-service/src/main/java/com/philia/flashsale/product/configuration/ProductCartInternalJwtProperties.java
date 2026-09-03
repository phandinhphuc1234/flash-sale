package com.philia.flashsale.product.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Trust settings for the least-privilege Cart-to-Product machine identity. */
@ConfigurationProperties(prefix = "flashsale.product.security.cart-internal-jwt")
public record ProductCartInternalJwtProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        String subject,
        String requiredScope) { }
