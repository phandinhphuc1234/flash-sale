package com.philia.flashsale.payment.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Kafka command, fact, DLT, and transactional-outbox runtime settings. */
@Validated
@ConfigurationProperties(prefix = "payment.kafka")
public record PaymentKafkaProperties(
        @NotBlank String bootstrapServers,
        @NotNull URI schemaRegistryUrl,
        @NotBlank String commandTopic,
        @NotBlank String eventTopic,
        @NotBlank String commandDltTopic,
        @NotBlank String consumerGroup,
        boolean consumerEnabled,
        boolean outboxPublisherEnabled,
        boolean autoRegisterSchemas,
        @NotNull List<Duration> retryDelays,
        @NotNull Duration outboxPollInterval,
        @Min(1) int outboxBatchSize,
        @NotNull Duration outboxClaimLease,
        @NotNull Duration outboxRetryBackoffCap) {
}
