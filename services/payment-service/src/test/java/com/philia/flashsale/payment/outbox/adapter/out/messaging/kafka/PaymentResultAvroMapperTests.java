package com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

/** Contract proof for both Payment result records and the safe outbox-to-Avro boundary. */
class PaymentResultAvroMapperTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentResultAvroMapper mapper = new PaymentResultAvroMapper(objectMapper);

    @Test
    void mapsSuccessEnvelopeDataDecimalAndNullableIntent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentOutboxEvent event = event("PaymentSucceeded", eventId, orderId,
                successPayload(eventId, paymentId, orderId, null));

        SpecificRecord result = mapper.toRecord(event);

        assertThat(result).isInstanceOf(PaymentSucceededV1.class);
        PaymentSucceededV1 record = (PaymentSucceededV1) result;
        assertThat(record.getEventId()).isEqualTo(eventId);
        assertThat(record.getAggregateId()).isEqualTo(paymentId);
        assertThat(record.getAggregateVersion()).isEqualTo(3L);
        assertThat(record.getCorrelationId()).isEqualTo(orderId);
        assertThat(record.getData().getPaymentId()).isEqualTo(paymentId);
        assertThat(record.getData().getOrderId()).isEqualTo(orderId);
        assertThat(record.getData().getAmount()).isEqualByComparingTo(new BigDecimal("125.5000"));
        assertThat(record.getData().getProviderPaymentIntentId()).isNull();
    }

    @Test
    void mapsFailureWithNullableSessionAndExactReason() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentFailedV1 record = (PaymentFailedV1) mapper.toRecord(event("PaymentFailed", eventId, orderId,
                failurePayload(eventId, paymentId, orderId, null)));

        assertThat(record.getEventType()).isEqualTo("PaymentFailed");
        assertThat(record.getData().getReason()).isEqualTo("PAYMENT_DEADLINE_EXPIRED");
        assertThat(record.getData().getProviderSessionId()).isNull();
        assertThat(record.getData().getCurrency()).isEqualTo("VND");
    }

    @Test
    void rejectsIdentityKeyVersionAndForbiddenFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var forbidden = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                successPayload(eventId, paymentId, orderId, "cs_test"));
        forbidden.put("checkoutUrl", "https://stripe.invalid/session");
        PaymentOutboxEvent event = event("PaymentSucceeded", eventId, orderId,
                objectMapper.writeValueAsString(forbidden));

        assertThatThrownBy(() -> mapper.toRecord(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden");
    }

    @Test
    void rejectsMismatchedOutboxIdentityAndNonExactDecimal() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = successPayload(eventId, paymentId, orderId, "cs_test")
                .replace("125.5000", "125.12345");
        PaymentOutboxEvent wrongIdentity = event("PaymentSucceeded", UUID.randomUUID(), orderId, payload);

        assertThatThrownBy(() -> mapper.toRecord(wrongIdentity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        assertThatThrownBy(() -> mapper.toRecord(event("PaymentSucceeded", eventId, orderId, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
    }

    private PaymentOutboxEvent event(String type, UUID eventId, UUID orderId, String payload) {
        return new PaymentOutboxEvent(eventId, UUID.fromString(payloadField(payload, "aggregateId")), 3,
                type, 1, "flashsale.payment.events.v1", orderId, payload, null, null,
                "IN_PROGRESS", 1, NOW, "worker", NOW.plusSeconds(30), null, NOW, null);
    }

    private String payloadField(String payload, String field) throws RuntimeException {
        try {
            return objectMapper.readTree(payload).get(field).textValue();
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private String successPayload(UUID eventId, UUID paymentId, UUID orderId, String sessionId)
            throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", "PaymentSucceeded");
        root.put("eventVersion", 1);
        root.put("producer", "payment-service");
        root.put("aggregateType", "PAYMENT");
        root.put("aggregateId", paymentId.toString());
        root.put("aggregateVersion", 3);
        root.put("correlationId", orderId.toString());
        root.put("causationId", UUID.randomUUID().toString());
        root.put("occurredAt", NOW.toString());
        var data = root.putObject("data");
        data.put("paymentId", paymentId.toString());
        data.put("orderId", orderId.toString());
        data.put("amount", new BigDecimal("125.5000"));
        data.put("currency", "VND");
        data.put("paidAt", NOW.toString());
        data.put("provider", "STRIPE");
        if (sessionId == null) {
            data.put("providerSessionId", "cs_test_default");
            data.putNull("providerPaymentIntentId");
        } else {
            data.put("providerSessionId", sessionId);
            data.putNull("providerPaymentIntentId");
        }
        return objectMapper.writeValueAsString(root);
    }

    private String failurePayload(UUID eventId, UUID paymentId, UUID orderId, String sessionId)
            throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", "PaymentFailed");
        root.put("eventVersion", 1);
        root.put("producer", "payment-service");
        root.put("aggregateType", "PAYMENT");
        root.put("aggregateId", paymentId.toString());
        root.put("aggregateVersion", 3);
        root.put("correlationId", orderId.toString());
        root.put("causationId", UUID.randomUUID().toString());
        root.put("occurredAt", NOW.toString());
        var data = root.putObject("data");
        data.put("paymentId", paymentId.toString());
        data.put("orderId", orderId.toString());
        data.put("amount", new BigDecimal("125.5000"));
        data.put("currency", "VND");
        data.put("failedAt", NOW.toString());
        data.put("reason", "PAYMENT_DEADLINE_EXPIRED");
        data.put("provider", "STRIPE");
        if (sessionId == null) {
            data.putNull("providerSessionId");
        } else {
            data.put("providerSessionId", sessionId);
        }
        return objectMapper.writeValueAsString(root);
    }
}
