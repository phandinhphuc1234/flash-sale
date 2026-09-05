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
        @NotBlank String paymentCommandsTopic,
        @NotBlank String paymentEventsTopic,
        @NotBlank String paymentEventsConsumerGroup,
        @NotBlank String paymentEventsDltTopic,
        @NotBlank String purchaseCommandsTopic,
        @NotBlank String purchaseReservationResultsTopic,
        @NotBlank String purchaseReservationResultsConsumerGroup,
        @NotBlank String purchaseReservationResultsDltTopic,
        @NotBlank String regularHoldCommandsTopic,
        @NotBlank String regularHoldEventsTopic,
        @NotBlank String regularHoldEventsConsumerGroup,
        @NotBlank String regularHoldEventsDltTopic) {

    /** Backward-compatible constructor for unit tests and local callers before result topics existed. */
    public OrderKafkaProperties(String bootstrapServers, String schemaRegistryUrl,
            String acceptedPurchaseTopic, String acceptedPurchaseConsumerGroup,
            String acceptedPurchaseDltTopic, String orderEventsTopic,
            List<Duration> retryDelays, String paymentCommandsTopic) {
        this(bootstrapServers, schemaRegistryUrl, acceptedPurchaseTopic, acceptedPurchaseConsumerGroup,
                acceptedPurchaseDltTopic, orderEventsTopic, retryDelays, paymentCommandsTopic,
                "flashsale.payment.events.v1", "order-payment-events-v1",
                "flashsale.order.payment-result.dlt.v1", "flashsale.purchase.commands.v1",
                "flashsale.purchase.events.v1", "order-purchase-reservation-results-v1",
                "flashsale.order.purchase-reservation-result.dlt.v1",
                "flashsale.inventory.regular-hold.commands.v1", "flashsale.inventory.regular-hold.events.v1",
                "order-regular-hold-events-v1", "flashsale.order.regular-hold-result.dlt.v1");
    }

    @ConstructorBinding
    public OrderKafkaProperties {
    }
}
