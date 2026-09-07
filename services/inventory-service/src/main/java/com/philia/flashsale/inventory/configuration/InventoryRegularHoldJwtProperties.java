package com.philia.flashsale.inventory.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Exact trust settings for Order's internal regular stock-hold capability. */
@ConfigurationProperties(prefix = "flashsale.inventory.security.regular-hold-jwt")
public record InventoryRegularHoldJwtProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        String subject,
        String requiredScope) { }
