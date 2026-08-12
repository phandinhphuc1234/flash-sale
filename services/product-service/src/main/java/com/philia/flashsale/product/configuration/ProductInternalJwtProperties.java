package com.philia.flashsale.product.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flashsale.product.security.internal-jwt")
public record ProductInternalJwtProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        String subject,
        String requiredScope) { }
