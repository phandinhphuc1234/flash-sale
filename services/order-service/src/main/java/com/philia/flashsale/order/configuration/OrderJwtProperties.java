package com.philia.flashsale.order.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public JWT trust values used by the future owner-query security adapter. */
@Validated
@ConfigurationProperties(prefix = "order.jwt")
public record OrderJwtProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String audience,
        @NotBlank String type) {
}
