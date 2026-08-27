package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedDataV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Maps the Order-owned PaymentRequested outbox snapshot to the existing Payment wire contract. */
public final class PaymentRequestedAvroMapper {
    private final ObjectMapper objectMapper;

    public PaymentRequestedAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public PaymentRequestedV1 map(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"PaymentRequested".equals(event.eventType()) || event.eventVersion() != 1
                || !"PURCHASE_SAGA".equals(event.aggregateType()) || event.aggregateVersion() != 1) {
            throw invalid("outbox envelope is not PaymentRequested.v1");
        }
        JsonNode payload = parse(event.payload());
        UUID orderId = uuid(payload, "orderId");
        if (!orderId.equals(event.aggregateId()) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("event key, aggregateId, and payload orderId must match");
        }
        UUID userId = uuid(payload, "userId");
        BigDecimal amount = money(payload, "amount");
        String currency = text(payload, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw invalid("currency must be three uppercase ASCII letters");
        }
        Instant deadline = instant(payload, "paymentDeadline");
        PaymentRequestedDataV1 data = new PaymentRequestedDataV1(orderId, userId, amount, currency, deadline);
        // The durable row is Saga-scoped, while the approved wire contract is Order-scoped.
        return new PaymentRequestedV1(event.eventId(), event.eventType(), event.eventVersion(),
                "order-service", "ORDER", orderId, event.aggregateVersion(), event.correlationId(),
                event.causationId(), event.occurredAt(), data);
    }

    private JsonNode parse(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw invalid("PaymentRequested payload must be an object");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("PaymentRequested payload cannot be decoded", exception);
        }
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException exception) {
            throw invalid("PaymentRequested field is not a UUID: " + field);
        }
    }

    private BigDecimal money(JsonNode node, String field) {
        try {
            BigDecimal value = new BigDecimal(text(node, field)).setScale(4, RoundingMode.UNNECESSARY);
            if (value.signum() <= 0 || value.precision() > 19) {
                throw invalid("PaymentRequested amount must be positive NUMERIC(19,4)");
            }
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid("PaymentRequested amount has invalid scale");
        }
    }

    private Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (RuntimeException exception) {
            throw invalid("PaymentRequested timestamp is invalid: " + field);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("PaymentRequested field is missing: " + field);
        }
        return value.asText();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
