package com.philia.flashsale.inventory.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flashsale.inventory.security.internal-jwt")
public record InventoryInternalJwtProperties(
                String issuer,
                String jwkSetUri,
                String audience,
                String subject,
                String requiredScope) {
}
