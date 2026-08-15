package com.philia.flashsale.order.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderPropertiesTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void supportedDefaultsRemainBoundedAndExplicit() {
        OrderKafkaProperties kafka = new OrderKafkaProperties(
                "kafka:9092",
                "http://schema-registry:8081",
                "flashsale.purchase.events.v1",
                "order-purchase-accepted-v1",
                "flashsale.order.purchase-accepted.dlt.v1",
                "flashsale.order.events.v1",
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10)));
        OrderOutboxProperties outbox = new OrderOutboxProperties(
                true, Duration.ofMillis(500), 100, Duration.ofSeconds(30), Duration.ofSeconds(60));
        OrderRuntimeProperties runtime = new OrderRuntimeProperties(true, true, 100);
        OrderJwtProperties jwt = new OrderJwtProperties(
                "http://authentication-service:8080",
                "http://authentication-service:8080/.well-known/jwks.json",
                "flash-sale-api",
                "at+jwt");

        assertThat(validator.validate(kafka)).isEmpty();
        assertThat(validator.validate(outbox)).isEmpty();
        assertThat(validator.validate(runtime)).isEmpty();
        assertThat(validator.validate(jwt)).isEmpty();
        assertThat(kafka.retryDelays()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10));
        assertThat(outbox.batchSize()).isEqualTo(100);
        assertThat(runtime.queryPageSizeMax()).isEqualTo(100);
    }

    @Test
    void blankRequiredValuesAndEmptyRetryListAreRejected() {
        OrderKafkaProperties invalidKafka = new OrderKafkaProperties(
                "", " ", "", "", "", "", List.of());
        OrderJwtProperties invalidJwt = new OrderJwtProperties("", " ", "", "");

        assertThat(validator.validate(invalidKafka)).extracting(v -> v.getPropertyPath().toString())
                .contains("bootstrapServers", "schemaRegistryUrl", "acceptedPurchaseTopic",
                        "acceptedPurchaseConsumerGroup", "acceptedPurchaseDltTopic", "orderEventsTopic",
                        "retryDelays");
        assertThat(validator.validate(invalidJwt)).extracting(v -> v.getPropertyPath().toString())
                .contains("issuer", "jwkSetUri", "audience", "type");
    }

    @Test
    void negativeAndOutOfRangeOperationalValuesAreRejected() {
        OrderOutboxProperties invalidOutbox = new OrderOutboxProperties(
                null, null, 0, null, null);
        OrderRuntimeProperties invalidRuntime = new OrderRuntimeProperties(null, null, 101);

        assertThat(validator.validate(invalidOutbox)).extracting(v -> v.getPropertyPath().toString())
                .contains("enabled", "pollInterval", "batchSize", "claimLease", "retryBackoffCap");
        assertThat(validator.validate(invalidRuntime)).extracting(v -> v.getPropertyPath().toString())
                .contains("acceptedPurchaseConsumerEnabled", "outboxPublisherEnabled", "queryPageSizeMax");
    }
}
