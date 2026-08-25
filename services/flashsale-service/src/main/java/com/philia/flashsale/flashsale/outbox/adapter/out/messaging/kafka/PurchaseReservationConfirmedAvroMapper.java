package com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps the confirmed-outcome JSON payload to its versioned Avro contract. */
@Component
public final class PurchaseReservationConfirmedAvroMapper {
    public PurchaseReservationConfirmedV1 map(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"PurchaseReservationConfirmed".equals(event.eventType()) || event.eventVersion() != 1
                || !"PURCHASE_RESERVATION".equals(event.aggregateType()) || event.causationId() == null) {
            throw new IllegalArgumentException("outbox envelope is not PurchaseReservationConfirmed.v1");
        }
        Map<String, Object> payload = event.payload();
        UUID sagaId = uuid(payload, "sagaId");
        UUID orderId = uuid(payload, "orderId");
        UUID purchaseRequestId = uuid(payload, "purchaseRequestId");
        UUID reservationId = uuid(payload, "reservationId");
        UUID paymentId = uuid(payload, "paymentId");
        if (!event.aggregateId().equals(reservationId)) {
            throw new IllegalArgumentException("confirmed outcome aggregate identity mismatch");
        }
        return new PurchaseReservationConfirmedV1(event.eventId(), event.eventType(), event.eventVersion(),
                "flashsale-service", event.aggregateType(), event.aggregateId(), event.aggregateVersion(),
                orderId, event.causationId(), event.createdAt(),
                new PurchaseReservationConfirmedDataV1(sagaId, orderId, purchaseRequestId, reservationId,
                        paymentId, instant(payload, "confirmedAt")));
    }

    private UUID uuid(Map<String, Object> payload, String field) {
        return UUID.fromString(text(payload, field));
    }

    private Instant instant(Map<String, Object> payload, String field) {
        return Instant.parse(text(payload, field));
    }

    private String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("outbox payload field is missing: " + field);
        }
        return value.toString();
    }
}
