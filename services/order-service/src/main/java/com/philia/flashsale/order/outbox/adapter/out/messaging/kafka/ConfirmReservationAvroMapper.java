package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationDataV1;
import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Maps a persisted confirm intent to the versioned Flash Sale command contract. */
public final class ConfirmReservationAvroMapper {
    private final ObjectMapper objectMapper;

    public ConfirmReservationAvroMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ConfirmPurchaseReservationV1 map(OrderOutboxEvent event) {
        if (event == null || !"ConfirmPurchaseReservation".equals(event.eventType())
                || event.eventVersion() != 1 || !"PURCHASE_SAGA".equals(event.aggregateType())) {
            throw invalid("outbox envelope is not ConfirmPurchaseReservation.v1");
        }
        JsonNode root = read(event.payload());
        UUID sagaId = uuid(root, "sagaId");
        UUID orderId = uuid(root, "orderId");
        UUID purchaseRequestId = uuid(root, "purchaseRequestId");
        UUID reservationId = uuid(root, "reservationId");
        UUID paymentId = uuid(root, "paymentId");
        Instant paidAt = instant(root, "paidAt");
        if (!event.aggregateId().equals(sagaId) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("confirm command identity/key mismatch");
        }
        return new ConfirmPurchaseReservationV1(event.eventId(), "ConfirmPurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, event.aggregateVersion(), event.correlationId(),
                event.causationId(), event.occurredAt(), new ConfirmPurchaseReservationDataV1(
                        sagaId, orderId, purchaseRequestId, reservationId, paymentId, paidAt));
    }

    private JsonNode read(String payload) {
        try {
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) throw invalid("payload must be an object");
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException("confirm payload is not valid JSON", exception);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private UUID uuid(JsonNode root, String field) {
        try { return UUID.fromString(root.path(field).asText()); }
        catch (RuntimeException exception) { throw invalid(field + " is not a UUID"); }
    }

    private Instant instant(JsonNode root, String field) {
        try { return Instant.parse(root.path(field).asText()); }
        catch (RuntimeException exception) { throw invalid(field + " is not an instant"); }
    }

    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
