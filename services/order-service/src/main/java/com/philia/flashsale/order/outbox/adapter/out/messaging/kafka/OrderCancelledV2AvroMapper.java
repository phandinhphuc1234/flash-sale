package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v2.OrderCancelledDataV2;
import com.philia.flashsale.contract.order.event.v2.OrderCancelledV2;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Maps the additive regular OrderCancelled V2 fact. */
public final class OrderCancelledV2AvroMapper {
    private final ObjectMapper objectMapper;

    public OrderCancelledV2AvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public OrderCancelledV2 map(OrderOutboxEvent event) {
        if (event == null || !"OrderCancelledV2".equals(event.eventType()) || event.eventVersion() != 2
                || !"ORDER".equals(event.aggregateType())) throw invalid("outbox envelope is not OrderCancelled.v2");
        JsonNode root = read(event.payload());
        UUID orderId = uuid(root, "orderId");
        if (!orderId.equals(event.aggregateId()) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderCancelledV2 identity/key mismatch");
        }
        return new OrderCancelledV2(event.eventId(), "OrderCancelled", 2, "order-service", "ORDER", orderId,
                event.aggregateVersion(), event.correlationId(), event.causationId(), event.occurredAt(),
                event.traceparent(), event.tracestate(), new OrderCancelledDataV2(orderId,
                        text(root, "orderNumber"), uuid(root, "purchaseRequestId"), oneOf(root, "purchaseSource"),
                        "REGULAR_STOCK_HOLD", uuid(root, "stockReferenceId"), text(root, "reason"),
                        instant(root, "cancelledAt")));
    }

    private JsonNode read(String payload) { try { JsonNode value = objectMapper.readTree(payload); if (value == null || !value.isObject()) throw invalid("payload must be an object"); return value; } catch (IOException exception) { throw invalid("OrderCancelledV2 payload is not valid JSON"); } }
    private String oneOf(JsonNode root, String field) { String value = text(root, field); if (!Set.of("BUY_NOW", "CART").contains(value)) throw invalid("unsupported " + field); return value; }
    private UUID uuid(JsonNode root, String field) { try { return UUID.fromString(text(root, field)); } catch (RuntimeException exception) { throw invalid(field + " is not a UUID"); } }
    private Instant instant(JsonNode root, String field) { try { return Instant.parse(text(root, field)); } catch (RuntimeException exception) { throw invalid(field + " is not an instant"); } }
    private String text(JsonNode root, String field) { JsonNode value = root == null ? null : root.get(field); if (value == null || !value.isTextual() || value.asText().isBlank()) throw invalid(field + " is missing"); return value.asText(); }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
