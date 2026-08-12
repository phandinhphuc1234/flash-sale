package com.philia.flashsale.flashsale.security.serviceidentity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Machine-token contract expected from Authentication Service for Campaign recovery. */
@Validated
@ConfigurationProperties(prefix = "flashsale.security.service-token")
public record FlashSaleServiceTokenProperties(
        @NotBlank String audience,
        @Min(1) int maxTtlSeconds) {
}
