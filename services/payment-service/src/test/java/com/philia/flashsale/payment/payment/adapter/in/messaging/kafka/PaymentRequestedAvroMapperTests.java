package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedDataV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** Contract guards for the PaymentRequested SpecificRecord to application-command boundary. */
class PaymentRequestedAvroMapperTests {

    private static final String TOPIC = "flashsale.payment.commands.v1";
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
    private static final Instant OCCURRED_AT = Instant.parse("2030-01-01T10:00:00Z");

    private final PaymentRequestedAvroMapper mapper = new PaymentRequestedAvroMapper(TOPIC);

    @Test
    void mapsEveryEnvelopeBusinessLogicalTypeAndW3cHeader() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, PaymentRequestedV1> record = record(orderId,
                event(eventId, orderId, new BigDecimal("125.0000"), "VND"));
        record.headers().add("traceparent", TRACEPARENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.headers().add("tracestate", "vendor=value".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var command = mapper.map(record);

        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.userId()).isNotNull();
        assertThat(command.amount()).isEqualByComparingTo("125.0000");
        assertThat(command.currency()).isEqualTo("VND");
        assertThat(command.paymentDeadline()).isEqualTo(OCCURRED_AT.plusSeconds(600));
        assertThat(command.traceparent()).isEqualTo(TRACEPARENT);
        assertThat(command.tracestate()).isEqualTo("vendor=value");
    }

    @Test
    void rejectsMessageKeyAggregateAndOrderMismatch() {
        UUID orderId = UUID.randomUUID();
        ConsumerRecord<String, PaymentRequestedV1> record = record(UUID.randomUUID(),
                event(UUID.randomUUID(), orderId, new BigDecimal("1.0000"), "VND"));

        assertThatThrownBy(() -> mapper.map(record))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("message key");
    }

    @Test
    void rejectsUnsupportedEnvelopeAndBusinessValuesWithoutLeakingPayload() {
        UUID orderId = UUID.randomUUID();
        PaymentRequestedV1 invalid = new PaymentRequestedV1(UUID.randomUUID(), "OrderCreated", 1,
                "order-service", "ORDER", orderId, 1L, UUID.randomUUID(), UUID.randomUUID(), OCCURRED_AT,
                data(orderId, new BigDecimal("1.0000"), "VND"));

        assertThatThrownBy(() -> mapper.map(record(orderId, invalid)))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessage("eventType is unsupported");
    }

    @Test
    void rejectsAmountScaleCurrencyAndMalformedTrace() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> mapper.map(record(orderId,
                event(UUID.randomUUID(), orderId, new BigDecimal("1.23456"), "VND"))))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("scale 4");

        assertThatThrownBy(() -> mapper.map(record(orderId,
                event(UUID.randomUUID(), orderId, new BigDecimal("1.0000"), "usd"))))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("currency");

        ConsumerRecord<String, PaymentRequestedV1> malformedTrace = record(orderId,
                event(UUID.randomUUID(), orderId, new BigDecimal("1.0000"), "VND"));
        malformedTrace.headers().add("traceparent", "not-a-trace".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> mapper.map(malformedTrace))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("traceparent");
    }

    private ConsumerRecord<String, PaymentRequestedV1> record(UUID key, PaymentRequestedV1 event) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, key.toString(), event);
    }

    private PaymentRequestedV1 event(UUID eventId, UUID orderId, BigDecimal amount, String currency) {
        return new PaymentRequestedV1(eventId, "PaymentRequested", 1, "order-service", "ORDER", orderId,
                1L, UUID.randomUUID(), UUID.randomUUID(), OCCURRED_AT, data(orderId, amount, currency));
    }

    private PaymentRequestedDataV1 data(UUID orderId, BigDecimal amount, String currency) {
        return new PaymentRequestedDataV1(orderId, UUID.randomUUID(), amount, currency,
                OCCURRED_AT.plusSeconds(600));
    }
}
