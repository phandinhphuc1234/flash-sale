package com.philia.flashsale.payment.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded recovery polling, lease, retry, and Stripe idempotency replay settings. */
@Validated
@ConfigurationProperties(prefix = "payment.recovery")
public record PaymentRecoveryProperties(
        boolean enabled,
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @NotNull Duration claimLease,
        @Min(1) int maxAttempts,
        @NotNull List<Duration> retryBackoff,
        @NotNull Duration safeReplayWindow) {
}
