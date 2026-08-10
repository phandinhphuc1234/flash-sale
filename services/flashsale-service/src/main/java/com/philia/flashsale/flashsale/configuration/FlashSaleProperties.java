package com.philia.flashsale.flashsale.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Runtime rules shared by reservation admission and idempotency cleanup. */
@Validated
@ConfigurationProperties(prefix = "flashsale.reservation")
public record FlashSaleProperties(
        @NotNull Duration ttl,
        @NotNull Duration idempotencyRetentionAfterCampaignEnd,
        @Min(1) @Max(128) int idempotencyKeyMaxLength) {
}
