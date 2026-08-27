package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderConfirmedDataV1;
import com.philia.flashsale.contract.order.event.v1.OrderConfirmedV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Maps the terminal Order outbox snapshot to OrderConfirmedV1. */
public final class OrderConfirmedAvroMapper {
    private final ObjectMapper objectMapper;

    public OrderConfirmedAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderConfirmedV1 map(OrderOutboxEvent event) {
        if (event == null || !"OrderConfirmed".equals(event.eventType())
                || event.eventVersion() != 1 || !"ORDER".equals(event.aggregateType())) {
            throw invalid("outbox envelope is not OrderConfirmed.v1");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(event.payload());
        } catch (IOException exception) {
            throw new IllegalArgumentException("OrderConfirmed payload is not valid JSON", exception);
        }
        UUID orderId = uuid(root, "orderId");
        if (!orderId.equals(event.aggregateId()) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderConfirmed identity/key mismatch");
        }
        String orderNumber = text(root, "orderNumber");
        return new OrderConfirmedV1(event.eventId(), event.eventType(), event.eventVersion(),
                "order-service", event.aggregateType(), event.aggregateId(), event.aggregateVersion(),
                event.correlationId(), event.causationId(), event.occurredAt(),
                new OrderConfirmedDataV1(orderId, orderNumber, uuid(root, "purchaseRequestId"),
                        uuid(root, "reservationId"), uuid(root, "paymentId"), instant(root, "confirmedAt")));
    }

    private UUID uuid(JsonNode root, String field) {
        try { return UUID.fromString(text(root, field)); }
        catch (RuntimeException exception) { throw invalid(field + " is not a UUID"); }
    }

    private Instant instant(JsonNode root, String field) {
        try { return Instant.parse(text(root, field)); }
        catch (RuntimeException exception) { throw invalid(field + " is not an instant"); }
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " is missing");
        }
        return value.asText();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
