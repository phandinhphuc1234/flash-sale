package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Validated process-local protection policy for Order's regular-stock-hold dependency call. */
@ConfigurationProperties("order.regular-purchase.inventory-resilience")
public record OrderInventoryResilienceProperties(
        @DefaultValue("20") int slidingWindowSize,
        @DefaultValue("10") int minimumNumberOfCalls,
        @DefaultValue("50") float failureRateThreshold,
        @DefaultValue("10s") Duration waitDurationInOpenState,
        @DefaultValue("2") int permittedCallsInHalfOpenState,
        @DefaultValue("false") boolean automaticTransition,
        @DefaultValue("16") int maxConcurrentCalls) {

    public OrderInventoryResilienceProperties {
        Objects.requireNonNull(waitDurationInOpenState, "waitDurationInOpenState is required");
        require(slidingWindowSize >= 2, "slidingWindowSize must be at least 2");
        require(minimumNumberOfCalls >= 1, "minimumNumberOfCalls must be at least 1");
        require(minimumNumberOfCalls <= slidingWindowSize,
                "minimumNumberOfCalls must not exceed slidingWindowSize");
        require(Float.isFinite(failureRateThreshold)
                        && failureRateThreshold > 0.0f
                        && failureRateThreshold <= 100.0f,
                "failureRateThreshold must be greater than 0 and at most 100");
        require(!waitDurationInOpenState.isZero() && !waitDurationInOpenState.isNegative(),
                "waitDurationInOpenState must be positive");
        require(permittedCallsInHalfOpenState >= 1,
                "permittedCallsInHalfOpenState must be at least 1");
        require(maxConcurrentCalls >= 1, "maxConcurrentCalls must be at least 1");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
