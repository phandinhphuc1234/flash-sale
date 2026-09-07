package com.philia.flashsale.order.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded operational controls for the disabled-by-default regular-intake recovery worker. */
@Validated
@ConfigurationProperties(prefix = "order.regular-purchase.recovery")
public record RegularPurchaseRecoveryProperties(
        @NotNull Duration pollInterval,
        @Min(1) @Max(500) int batchSize,
        @NotNull Duration claimLease) {
}
