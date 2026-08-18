package com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

/** Verifies topic, order key, generated record type, stable identity, and headers. */
class PaymentResultKafkaPublisherTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentResultAvroMapper mapper = new PaymentResultAvroMapper(objectMapper);

    @Test
    void publishesSuccessWithOrderKeyAndTraceHeaders() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentOutboxEvent event = event("PaymentSucceeded", eventId, paymentId, orderId,
                successPayload(eventId, paymentId, orderId),
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        KafkaPaymentResultPublisher publisher = new KafkaPaymentResultPublisher(null, mapper,
                "flashsale.payment.events.v1");
        ProducerRecord<String, SpecificRecord> record = publisher.toProducerRecord(event);

        assertThat(record.topic()).isEqualTo("flashsale.payment.events.v1");
        assertThat(record.key()).isEqualTo(orderId.toString());
        assertThat(record.value()).isInstanceOf(PaymentSucceededV1.class);
        assertThat(header(record, "eventId")).isEqualTo(eventId.toString());
        assertThat(header(record, "eventType")).isEqualTo("PaymentSucceeded");
        assertThat(header(record, "eventVersion")).isEqualTo("1");
        assertThat(header(record, "contentType")).isEqualTo("application/avro");
        assertThat(header(record, "traceparent"))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    void publishesFailureAndDerivesValidTraceForWebhookOutcome() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentOutboxEvent event = event("PaymentFailed", eventId, paymentId, orderId,
                failurePayload(eventId, paymentId, orderId), null);

        ProducerRecord<String, SpecificRecord> record = new KafkaPaymentResultPublisher(null, mapper,
                "flashsale.payment.events.v1").toProducerRecord(event);

        assertThat(record.value()).isInstanceOf(PaymentFailedV1.class);
        assertThat(header(record, "traceparent"))
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void rejectsConfiguredTopicMismatchAndMalformedStoredTrace() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentOutboxEvent event = event("PaymentSucceeded", eventId, paymentId, orderId,
                successPayload(eventId, paymentId, orderId), "not-a-trace");
        KafkaPaymentResultPublisher publisher = new KafkaPaymentResultPublisher(null, mapper,
                "flashsale.payment.events.v1");

        assertThatThrownBy(() -> publisher.toProducerRecord(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceparent");
        assertThatThrownBy(() -> new KafkaPaymentResultPublisher(null, mapper, "other.topic")
                .toProducerRecord(event(event, "flashsale.payment.events.v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    private PaymentOutboxEvent event(String type, UUID eventId, UUID paymentId, UUID orderId,
            String payload, String traceparent) {
        return new PaymentOutboxEvent(eventId, paymentId, 3, type, 1,
                "flashsale.payment.events.v1", orderId, payload, traceparent, null,
                "IN_PROGRESS", 1, NOW, "worker", NOW.plusSeconds(30), null, NOW, null);
    }

    private PaymentOutboxEvent event(PaymentOutboxEvent event, String topic) {
        return new PaymentOutboxEvent(event.eventId(), event.aggregateId(), event.aggregateVersion(),
                event.eventType(), event.eventVersion(), topic, event.messageKey(), event.payload(),
                event.traceparent(), event.tracestate(), event.status(), event.attemptCount(),
                event.nextAttemptAt(), event.leaseOwner(), event.leaseUntil(), event.publishedAt(),
                event.createdAt(), event.lastErrorCode());
    }

    private String successPayload(UUID eventId, UUID paymentId, UUID orderId) throws Exception {
        var root = envelope(objectMapper, eventId, paymentId, orderId, "PaymentSucceeded");
        var data = root.putObject("data");
        data.put("paymentId", paymentId.toString());
        data.put("orderId", orderId.toString());
        data.put("amount", new BigDecimal("125.5000"));
        data.put("currency", "VND");
        data.put("paidAt", NOW.toString());
        data.put("provider", "STRIPE");
        data.put("providerSessionId", "cs_test_123");
        data.putNull("providerPaymentIntentId");
        return objectMapper.writeValueAsString(root);
    }

    private String failurePayload(UUID eventId, UUID paymentId, UUID orderId) throws Exception {
        var root = envelope(objectMapper, eventId, paymentId, orderId, "PaymentFailed");
        var data = root.putObject("data");
        data.put("paymentId", paymentId.toString());
        data.put("orderId", orderId.toString());
        data.put("amount", new BigDecimal("125.5000"));
        data.put("currency", "VND");
        data.put("failedAt", NOW.toString());
        data.put("reason", "PAYMENT_DEADLINE_EXPIRED");
        data.put("provider", "STRIPE");
        data.putNull("providerSessionId");
        return objectMapper.writeValueAsString(root);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode envelope(ObjectMapper mapper, UUID eventId,
            UUID paymentId, UUID orderId, String type) {
        var root = mapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", type);
        root.put("eventVersion", 1);
        root.put("producer", "payment-service");
        root.put("aggregateType", "PAYMENT");
        root.put("aggregateId", paymentId.toString());
        root.put("aggregateVersion", 3);
        root.put("correlationId", orderId.toString());
        root.put("causationId", UUID.randomUUID().toString());
        root.put("occurredAt", NOW.toString());
        return root;
    }

    private String header(ProducerRecord<String, SpecificRecord> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
