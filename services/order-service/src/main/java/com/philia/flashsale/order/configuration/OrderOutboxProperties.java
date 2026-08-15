package com.philia.flashsale.order.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed runtime controls for the durable Order-created outbox relay. */
@Validated
@ConfigurationProperties(prefix = "order.outbox")
public record OrderOutboxProperties(
        @NotNull Boolean enabled,
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @NotNull Duration claimLease,
        @NotNull Duration retryBackoffCap) {
}
