package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedDataV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Publishes one local-only PaymentRequested fixture for the Feature 021 smoke workflow. */
@EnabledIfSystemProperty(named = "payment.smoke.fixture", matches = "true")
class PaymentRequestedSmokeFixturePublisherTests {

    @Test
    void publishesApprovedPaymentRequestedFixture() throws Exception {
        UUID orderId = requiredUuid("payment.smoke.order-id");
        UUID userId = requiredUuid("payment.smoke.user-id");
        Instant occurredAt = Instant.now();
        var event = new PaymentRequestedV1(
                UUID.randomUUID(), "PaymentRequested", 1, "order-service", "ORDER",
                orderId, 1L, orderId, UUID.randomUUID(), occurredAt,
                new PaymentRequestedDataV1(orderId, userId, new BigDecimal("100000.0000"), "VND",
                        occurredAt.plus(Duration.ofHours(1))));

        try (var producer = PaymentRequestedKafkaLiveTestSupport.producer()) {
            var metadata = PaymentRequestedKafkaLiveTestSupport.send(producer, orderId.toString(), event);
            assertThat(metadata.topic()).isEqualTo(PaymentRequestedKafkaLiveTestSupport.COMMAND_TOPIC);
            assertThat(metadata.partition()).isNotNegative();
        }
    }

    private UUID requiredUuid(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be supplied by the local smoke workflow");
        }
        return UUID.fromString(value);
    }
}
