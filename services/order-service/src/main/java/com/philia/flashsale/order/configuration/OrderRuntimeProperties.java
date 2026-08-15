package com.philia.flashsale.order.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Feature switches and bounded query settings for the Order runtime. */
@Validated
@ConfigurationProperties(prefix = "order.runtime")
public record OrderRuntimeProperties(
        @NotNull Boolean acceptedPurchaseConsumerEnabled,
        @NotNull Boolean outboxPublisherEnabled,
        @Min(1) @Max(100) int queryPageSizeMax) {
}
