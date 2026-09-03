package com.philia.flashsale.cart.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public shopper-token trust settings owned by Cart's inbound security adapter. */
@Validated
@ConfigurationProperties(prefix = "cart.security.jwt")
public record CartJwtProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String audience,
        @NotBlank String type) {
}
