package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PaymentSucceededAvroMapperTests {
    private static final String TOPIC = "flashsale.payment.events.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsThePaymentSuccessAndKeepsOrderKeyIdentity() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentSucceededV1 event = event(orderId, paymentId);
        ConsumerRecord<String, PaymentSucceededV1> record = new ConsumerRecord<>(TOPIC, 1, 12,
                orderId.toString(), event);

        PaymentSucceededCommand command = new PaymentSucceededAvroMapper(TOPIC).map(record);

        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.paymentId()).isEqualTo(paymentId);
        assertThat(command.aggregateVersion()).isEqualTo(2);
        assertThat(command.fingerprint()).hasSize(64);
    }

    @Test
    void rejectsARecordWhoseKafkaKeyDoesNotMatchTheOrder() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new PaymentSucceededAvroMapper(TOPIC).map(
                new ConsumerRecord<>(TOPIC, 0, 0, UUID.randomUUID().toString(), event(orderId, UUID.randomUUID()))))
                .isInstanceOf(PaymentSucceededRecordException.class)
                .hasMessageContaining("message key");
    }

    private PaymentSucceededV1 event(UUID orderId, UUID paymentId) {
        return new PaymentSucceededV1(UUID.randomUUID(), "PaymentSucceeded", 1, "payment-service", "PAYMENT",
                paymentId, 2L, orderId, UUID.randomUUID(), NOW,
                new PaymentSucceededDataV1(paymentId, orderId, new BigDecimal("20.0000"), "VND",
                        NOW.plusSeconds(1), "stripe", "cs_test_123", "pi_test_123"));
    }
}
