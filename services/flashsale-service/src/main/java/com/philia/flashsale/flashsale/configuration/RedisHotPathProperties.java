package com.philia.flashsale.flashsale.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Redis Stream polling and reclaim settings for the single-instance MVP. */
@Validated
@ConfigurationProperties(prefix = "flashsale.redis")
public record RedisHotPathProperties(
        @NotBlank String handoffStream,
        @NotBlank String consumerGroup,
        @NotNull Duration pollTimeout,
        @Min(1) int batchSize,
        @NotNull Duration reclaimIdle) {
}
