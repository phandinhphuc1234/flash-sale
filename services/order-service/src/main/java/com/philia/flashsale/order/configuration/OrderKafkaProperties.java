package com.philia.flashsale.order.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/** Typed Kafka topics, identities, and retry settings owned by Order Service. */
@Validated
@ConfigurationProperties(prefix = "order.kafka")
public record OrderKafkaProperties(
        @NotBlank String bootstrapServers,
        @NotBlank String schemaRegistryUrl,
        @NotBlank String acceptedPurchaseTopic,
        @NotBlank String acceptedPurchaseConsumerGroup,
        @NotBlank String acceptedPurchaseDltTopic,
        @NotBlank String orderEventsTopic,
        @NotEmpty List<@NotNull Duration> retryDelays,
        @NotBlank String paymentCommandsTopic) {

    @ConstructorBinding
    public OrderKafkaProperties {
    }
}
