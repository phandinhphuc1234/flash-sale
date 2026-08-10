package com.philia.flashsale.flashsale.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Durable outbox polling and retry settings. */
@Validated
@ConfigurationProperties(prefix = "flashsale.outbox")
public record OutboxProperties(
        @NotBlank String topic,
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @NotNull Duration claimLease,
        @NotNull Duration retryBackoffCap) {
}
