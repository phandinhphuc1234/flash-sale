package com.philia.flashsale.flashsale.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Kafka projection and recovery settings; recovery never belongs to the purchase hot path. */
@Validated
@ConfigurationProperties(prefix = "flashsale.campaign-projection")
public record CampaignProjectionProperties(
        @NotBlank String topic,
        @NotBlank String consumerGroup,
        @NotNull Duration recoveryInterval,
        @Min(1) int maxDeliveries,
        @NotNull List<Duration> retryDelays,
        @NotNull URI snapshotBaseUrl) {
}
