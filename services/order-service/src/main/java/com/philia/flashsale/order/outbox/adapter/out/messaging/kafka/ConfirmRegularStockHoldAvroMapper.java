package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldDataV1;
import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Maps one persisted regular-hold confirmation intent to the strict Inventory command contract. */
public final class ConfirmRegularStockHoldAvroMapper {
    private static final String EVENT_TYPE = "ConfirmRegularStockHold";

    private final ObjectMapper objectMapper;

    public ConfirmRegularStockHoldAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ConfirmRegularStockHoldV1 map(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!EVENT_TYPE.equals(event.eventType()) || event.eventVersion() != 1
                || !"PURCHASE_SAGA".equals(event.aggregateType()) || event.aggregateVersion() < 1) {
            throw invalid("outbox envelope is not ConfirmRegularStockHold.v1");
        }
        JsonNode root = read(event.payload());
        UUID sagaId = uuid(root, "sagaId");
        UUID orderId = uuid(root, "orderId");
        UUID purchaseRequestId = uuid(root, "purchaseRequestId");
        UUID holdId = uuid(root, "holdId");
        UUID paymentId = uuid(root, "paymentId");
        Instant paidAt = instant(root, "paidAt");
        if (!event.aggregateId().equals(sagaId) || !event.eventKey().equals(orderId.toString())
                || !event.correlationId().equals(purchaseRequestId) || !sagaId.equals(purchaseRequestId)
                || event.causationId() == null) {
            throw invalid("regular hold confirm identity/key mismatch");
        }
        return new ConfirmRegularStockHoldV1(event.eventId(), EVENT_TYPE, 1, "order-service",
                "PURCHASE_SAGA", sagaId, event.aggregateVersion(), purchaseRequestId, event.causationId(),
                event.occurredAt(), event.traceparent(), event.tracestate(),
                new ConfirmRegularStockHoldDataV1(sagaId, orderId, purchaseRequestId, holdId, paymentId, paidAt));
    }

    private JsonNode read(String payload) {
        try {
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) throw invalid("payload must be an object");
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException("regular hold confirm payload is not valid JSON", exception);
        }
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asText());
        } catch (RuntimeException exception) {
            throw invalid(field + " is not a UUID");
        }
    }

    private Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(node.path(field).asText());
        } catch (RuntimeException exception) {
            throw invalid(field + " is not an instant");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
