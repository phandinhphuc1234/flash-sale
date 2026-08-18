package com.philia.flashsale.payment.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Leased webhook worker settings; disabled by default until the runtime is provisioned. */
@Validated
@ConfigurationProperties(prefix = "payment.webhook.processing")
public record ProviderEventProcessingProperties(
        boolean enabled,
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @NotNull Duration claimLease,
        @Min(1) int maxAttempts,
        @NotNull Duration retryBackoff) {
}
