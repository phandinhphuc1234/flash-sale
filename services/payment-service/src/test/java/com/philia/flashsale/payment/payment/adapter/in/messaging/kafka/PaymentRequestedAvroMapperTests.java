package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.OCCURRED_AT;
import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.TOPIC;
import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.TRACEPARENT;
import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.consumerRecord;
import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.data;
import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import java.math.BigDecimal;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** Contract guards for the PaymentRequested SpecificRecord to application-command boundary. */
class PaymentRequestedAvroMapperTests {

    private final PaymentRequestedAvroMapper mapper = new PaymentRequestedAvroMapper(TOPIC);

    @Test
    void mapsEveryEnvelopeBusinessLogicalTypeAndW3cHeader() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, PaymentRequestedV1> consumerRecord = consumerRecord(orderId,
                event(eventId, orderId, new BigDecimal("125.0000"), "VND"));
        consumerRecord.headers().add("traceparent", TRACEPARENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        consumerRecord.headers().add("tracestate", "vendor=value".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var command = mapper.map(consumerRecord);

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
        ConsumerRecord<String, PaymentRequestedV1> consumerRecord = consumerRecord(UUID.randomUUID(),
                event(UUID.randomUUID(), orderId, new BigDecimal("1.0000"), "VND"));

        assertThatThrownBy(() -> mapper.map(consumerRecord))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("message key");
    }

    @Test
    void rejectsUnsupportedEnvelopeAndBusinessValuesWithoutLeakingPayload() {
        UUID orderId = UUID.randomUUID();
        PaymentRequestedV1 invalid = new PaymentRequestedV1(UUID.randomUUID(), "OrderCreated", 1,
                "order-service", "ORDER", orderId, 1L, UUID.randomUUID(), UUID.randomUUID(), OCCURRED_AT,
                data(orderId, new BigDecimal("1.0000"), "VND"));

        assertThatThrownBy(() -> mapper.map(consumerRecord(orderId, invalid)))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessage("eventType is unsupported");
    }

    @Test
    void rejectsAmountScaleCurrencyAndMalformedTrace() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> mapper.map(consumerRecord(orderId,
                event(UUID.randomUUID(), orderId, new BigDecimal("1.23456"), "VND"))))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("scale 4");

        assertThatThrownBy(() -> mapper.map(consumerRecord(orderId,
                event(UUID.randomUUID(), orderId, new BigDecimal("1.0000"), "usd"))))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("currency");

        ConsumerRecord<String, PaymentRequestedV1> malformedTrace = consumerRecord(orderId,
                event(UUID.randomUUID(), orderId, new BigDecimal("1.0000"), "VND"));
        malformedTrace.headers().add("traceparent", "not-a-trace".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> mapper.map(malformedTrace))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining("traceparent");
    }

}
