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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Contract guards for the PaymentRequested SpecificRecord to application-command boundary. */
class PaymentRequestedAvroMapperTests {

    private final PaymentRequestedAvroMapper mapper = new PaymentRequestedAvroMapper(TOPIC);

    @Test
    void mapsEveryEnvelopeBusinessLogicalTypeAndW3cHeader() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, PaymentRequestedV1> consumerRecord = consumerRecord(orderId,
                event(eventId, orderId, new BigDecimal("125.0000"), "VND"));
        consumerRecord.headers().add("traceparent", TRACEPARENT.getBytes(StandardCharsets.UTF_8));
        consumerRecord.headers().add("tracestate", "vendor=value".getBytes(StandardCharsets.UTF_8));

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

    @ParameterizedTest(name = "rejects invalid payload: {1}")
    @MethodSource("invalidRecords")
    void rejectsInvalidRecords(ConsumerRecord<String, PaymentRequestedV1> payloadRecord,
            String expectedMessage) {
        assertThatThrownBy(() -> mapper.map(payloadRecord))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidRecords() {
        UUID scaleOrderId = UUID.randomUUID();
        UUID currencyOrderId = UUID.randomUUID();
        UUID traceOrderId = UUID.randomUUID();
        ConsumerRecord<String, PaymentRequestedV1> malformedTrace = consumerRecord(traceOrderId,
                event(UUID.randomUUID(), traceOrderId, new BigDecimal("1.0000"), "VND"));
        malformedTrace.headers().add("traceparent", "not-a-trace".getBytes(StandardCharsets.UTF_8));

        return Stream.of(
                Arguments.of(consumerRecord(scaleOrderId,
                        event(UUID.randomUUID(), scaleOrderId, new BigDecimal("1.23456"), "VND")), "scale 4"),
                Arguments.of(consumerRecord(currencyOrderId,
                        event(UUID.randomUUID(), currencyOrderId, new BigDecimal("1.0000"), "usd")), "currency"),
                Arguments.of(malformedTrace, "traceparent"));
    }

}
