package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldDataV1;
import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Maps a persisted regular-hold release intent to the strict Inventory command contract. */
public final class ReleaseRegularStockHoldAvroMapper {
    private final ObjectMapper objectMapper;

    public ReleaseRegularStockHoldAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ReleaseRegularStockHoldV1 map(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"ReleaseRegularStockHold".equals(event.eventType()) || event.eventVersion() != 1
                || !"PURCHASE_SAGA".equals(event.aggregateType()) || event.aggregateVersion() < 1) {
            throw invalid("outbox envelope is not ReleaseRegularStockHold.v1");
        }
        JsonNode root = read(event.payload());
        UUID sagaId = uuid(root, "sagaId");
        UUID orderId = uuid(root, "orderId");
        UUID purchaseRequestId = uuid(root, "purchaseRequestId");
        UUID holdId = uuid(root, "holdId");
        UUID paymentId = uuid(root, "paymentId");
        String reason = text(root, "reason");
        String desiredOrderStatus = oneOf(root, "desiredOrderStatus", Set.of("CANCELLED", "EXPIRED"));
        if (!event.aggregateId().equals(sagaId) || !event.eventKey().equals(orderId.toString())
                || !event.correlationId().equals(purchaseRequestId) || !sagaId.equals(purchaseRequestId)
                || event.causationId() == null) {
            throw invalid("regular hold release identity/key mismatch");
        }
        return new ReleaseRegularStockHoldV1(event.eventId(), "ReleaseRegularStockHold", 1, "order-service",
                "PURCHASE_SAGA", sagaId, event.aggregateVersion(), purchaseRequestId, event.causationId(),
                event.occurredAt(), event.traceparent(), event.tracestate(),
                new ReleaseRegularStockHoldDataV1(sagaId, orderId, purchaseRequestId, holdId, paymentId, reason,
                        desiredOrderStatus));
    }

    private JsonNode read(String payload) {
        try {
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) throw invalid("payload must be an object");
            return value;
        } catch (IOException exception) {
            throw invalid("regular hold release payload is not valid JSON");
        }
    }

    private UUID uuid(JsonNode root, String field) {
        try { return UUID.fromString(text(root, field)); }
        catch (RuntimeException exception) { throw invalid(field + " is not a UUID"); }
    }

    private String oneOf(JsonNode root, String field, Set<String> allowed) {
        String value = text(root, field);
        if (!allowed.contains(value)) throw invalid("unsupported " + field);
        return value;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw invalid(field + " is missing");
        return value.asText();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
