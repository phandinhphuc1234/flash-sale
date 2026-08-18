package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedDataV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Test-only wire and command fixtures for the PaymentRequested Kafka boundary. */
public final class PaymentRequestedKafkaTestFixtures {

    public static final String TOPIC = "flashsale.payment.commands.v1";
    public static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
    public static final Instant OCCURRED_AT = Instant.parse("2030-01-01T10:00:00Z");

    private PaymentRequestedKafkaTestFixtures() {
    }

    public static ConsumerRecord<String, PaymentRequestedV1> consumerRecord(
            UUID key, PaymentRequestedV1 payload) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, key.toString(), payload);
    }

    public static PaymentRequestedV1 event(UUID eventId, UUID orderId, BigDecimal amount, String currency) {
        return new PaymentRequestedV1(eventId, "PaymentRequested", 1, "order-service", "ORDER", orderId,
                1L, UUID.randomUUID(), UUID.randomUUID(), OCCURRED_AT, data(orderId, amount, currency));
    }

    public static PaymentRequestedDataV1 data(UUID orderId, BigDecimal amount, String currency) {
        return new PaymentRequestedDataV1(orderId, UUID.randomUUID(), amount, currency,
                OCCURRED_AT.plusSeconds(600));
    }

    public static AcceptPaymentRequestCommand command() {
        UUID orderId = UUID.randomUUID();
        return new AcceptPaymentRequestCommand(UUID.randomUUID(), "PaymentRequested", 1, "order-service", "ORDER",
                orderId, 1L, UUID.randomUUID(), UUID.randomUUID(), OCCURRED_AT, orderId, UUID.randomUUID(),
                new BigDecimal("1.0000"), "VND", OCCURRED_AT.plusSeconds(600), null, null);
    }
}
