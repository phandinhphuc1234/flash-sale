package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationDataV1;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.util.UUID;

/** Maps a persisted release intent to the versioned Flash Sale command contract. */
public final class ReleaseReservationAvroMapper {
    private final ObjectMapper objectMapper;

    public ReleaseReservationAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReleasePurchaseReservationV1 map(OrderOutboxEvent event) {
        if (event == null || !"ReleasePurchaseReservation".equals(event.eventType()) || event.eventVersion() != 1
                || !"PURCHASE_SAGA".equals(event.aggregateType())) {
            throw invalid("outbox envelope is not ReleasePurchaseReservation.v1");
        }
        JsonNode root = read(event.payload());
        UUID sagaId = uuid(root, "sagaId");
        UUID orderId = uuid(root, "orderId");
        UUID purchaseRequestId = uuid(root, "purchaseRequestId");
        UUID reservationId = uuid(root, "reservationId");
        String reason = text(root, "reason");
        if (!event.aggregateId().equals(sagaId) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("release command identity/key mismatch");
        }
        return new ReleasePurchaseReservationV1(event.eventId(), "ReleasePurchaseReservation", 1, "order-service",
                "PURCHASE_SAGA", sagaId, event.aggregateVersion(), event.correlationId(), event.causationId(), event.occurredAt(),
                new ReleasePurchaseReservationDataV1(sagaId, orderId, purchaseRequestId, reservationId, reason));
    }

    private JsonNode read(String payload) {
        try {
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) {
                throw invalid("payload must be an object");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException("release payload is not valid JSON", exception);
        }
    }

    private UUID uuid(JsonNode root, String field) {
        try {
            return UUID.fromString(root.path(field).asText());
        } catch (RuntimeException exception) {
            throw invalid(field + " is not a UUID");
        }
    }

    private String text(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalid(field + " is missing");
        }
        return value;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
