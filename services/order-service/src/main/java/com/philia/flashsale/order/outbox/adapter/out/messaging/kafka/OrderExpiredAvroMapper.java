package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderExpiredDataV1;
import com.philia.flashsale.contract.order.event.v1.OrderExpiredV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Maps the terminal expiry snapshot to OrderExpiredV1. */
public final class OrderExpiredAvroMapper {
    private final ObjectMapper objectMapper;
    public OrderExpiredAvroMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    public OrderExpiredV1 map(OrderOutboxEvent event) {
        if (event == null || !"OrderExpired".equals(event.eventType()) || event.eventVersion() != 1
                || !"ORDER".equals(event.aggregateType())) {
            throw invalid("outbox envelope is not OrderExpired.v1");
        }
        JsonNode root; try { root = objectMapper.readTree(event.payload()); } catch (IOException exception) { throw invalid("OrderExpired payload is not valid JSON"); }
        UUID orderId = uuid(root, "orderId");
        if (!orderId.equals(event.aggregateId()) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderExpired identity/key mismatch");
        }
        return new OrderExpiredV1(event.eventId(), event.eventType(), event.eventVersion(), "order-service", event.aggregateType(), event.aggregateId(), event.aggregateVersion(), event.correlationId(), event.causationId(), event.occurredAt(), new OrderExpiredDataV1(orderId, text(root, "orderNumber"), uuid(root, "purchaseRequestId"), uuid(root, "reservationId"), text(root, "reason"), instant(root, "expiredAt")));
    }
    private UUID uuid(JsonNode root, String field) { try { return UUID.fromString(text(root, field)); } catch (RuntimeException exception) { throw invalid(field + " is not a UUID"); } }
    private Instant instant(JsonNode root, String field) { try { return Instant.parse(text(root, field)); } catch (RuntimeException exception) { throw invalid(field + " is not an instant"); } }
    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " is missing");
        }
        return value.asText();
    }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
