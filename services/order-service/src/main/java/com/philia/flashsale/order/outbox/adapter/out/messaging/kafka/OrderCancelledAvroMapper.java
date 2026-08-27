package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderCancelledDataV1;
import com.philia.flashsale.contract.order.event.v1.OrderCancelledV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Maps the terminal cancellation snapshot to OrderCancelledV1. */
public final class OrderCancelledAvroMapper {
    private final ObjectMapper objectMapper;

    public OrderCancelledAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderCancelledV1 map(OrderOutboxEvent event) {
        if (event == null || !"OrderCancelled".equals(event.eventType()) || event.eventVersion() != 1
                || !"ORDER".equals(event.aggregateType())) {
            throw invalid("outbox envelope is not OrderCancelled.v1");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(event.payload());
        } catch (IOException exception) {
            throw invalid("OrderCancelled payload is not valid JSON");
        }
        UUID orderId = uuid(root, "orderId");
        if (!orderId.equals(event.aggregateId()) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderCancelled identity/key mismatch");
        }
        return new OrderCancelledV1(event.eventId(), event.eventType(), event.eventVersion(), "order-service",
                event.aggregateType(), event.aggregateId(), event.aggregateVersion(), event.correlationId(),
                event.causationId(), event.occurredAt(),
                new OrderCancelledDataV1(orderId, text(root, "orderNumber"), uuid(root, "purchaseRequestId"),
                        uuid(root, "reservationId"), text(root, "reason"), instant(root, "cancelledAt")));
    }

    private UUID uuid(JsonNode root, String field) {
        try {
            return UUID.fromString(text(root, field));
        } catch (RuntimeException exception) {
            throw invalid(field + " is not a UUID");
        }
    }

    private Instant instant(JsonNode root, String field) {
        try {
            return Instant.parse(text(root, field));
        } catch (RuntimeException exception) {
            throw invalid(field + " is not an instant");
        }
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
