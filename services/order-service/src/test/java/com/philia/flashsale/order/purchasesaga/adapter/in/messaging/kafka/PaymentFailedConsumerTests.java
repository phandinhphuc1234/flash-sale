package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.payment.event.v1.PaymentFailedDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PaymentFailedConsumerTests {
    private static final String TOPIC = "flashsale.payment.events.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsApprovedFailureReasonsAndKeepsOrderKeyIdentity() {
        UUID orderId = UUID.randomUUID();
        PaymentFailedV1 event = event(orderId, "PAYMENT_DEADLINE_EXPIRED", 2L);
        PaymentFailedCommand command = new PaymentFailedAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 1, 12, orderId.toString(), event));

        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.reason()).isEqualTo("PAYMENT_DEADLINE_EXPIRED");
        assertThat(command.fingerprint()).hasSize(64);
    }

    @Test
    void rejectsUnknownFailureReasonBeforeItReachesTheSaga() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new PaymentFailedAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 0, 0, orderId.toString(), event(orderId, "CARD_DECLINED", 1L))))
                .isInstanceOf(PaymentFailedRecordException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void rejectsARecordWhoseKafkaKeyDoesNotMatchTheOrder() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new PaymentFailedAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 0, 0, UUID.randomUUID().toString(), event(orderId,
                        "PROVIDER_TERMINAL_FAILURE", 1L))))
                .isInstanceOf(PaymentFailedRecordException.class)
                .hasMessageContaining("message key");
    }

    @Test
    void rejectsNonMonotonicPaymentVersion() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new PaymentFailedAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 0, 0, orderId.toString(), event(orderId,
                        "CHECKOUT_ATTEMPT_LIMIT_REACHED", 0L))))
                .isInstanceOf(PaymentFailedRecordException.class)
                .hasMessageContaining("version");
    }

    private PaymentFailedV1 event(UUID orderId, String reason, long version) {
        UUID paymentId = UUID.randomUUID();
        return new PaymentFailedV1(UUID.randomUUID(), "PaymentFailed", 1, "payment-service", "PAYMENT",
                paymentId, version, orderId, UUID.randomUUID(), NOW,
                new PaymentFailedDataV1(paymentId, orderId, new BigDecimal("20.0000"), "VND",
                        NOW.plusSeconds(1), reason, "stripe", "cs_test_123"));
    }
}
