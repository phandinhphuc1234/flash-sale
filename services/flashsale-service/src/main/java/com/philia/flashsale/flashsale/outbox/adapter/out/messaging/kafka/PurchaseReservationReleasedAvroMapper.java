package com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps the released/expired reservation outcome JSON to its Avro contract. */
@Component
public final class PurchaseReservationReleasedAvroMapper {
    public PurchaseReservationReleasedV1 map(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"PurchaseReservationReleased".equals(event.eventType()) || event.eventVersion() != 1
                || !"PURCHASE_RESERVATION".equals(event.aggregateType()) || event.causationId() == null) {
            throw new IllegalArgumentException("outbox envelope is not PurchaseReservationReleased.v1");
        }
        Map<String, Object> payload = event.payload();
        UUID reservationId = uuid(payload, "reservationId");
        String status = text(payload, "reservationStatus");
        if (!"RELEASED".equals(status) && !"EXPIRED".equals(status)) {
            throw new IllegalArgumentException("reservation status is unsupported");
        }
        UUID sagaId = uuid(payload, "sagaId");
        return new PurchaseReservationReleasedV1(event.eventId(), event.eventType(), event.eventVersion(),
                "flashsale-service", event.aggregateType(), event.aggregateId(), event.aggregateVersion(),
                sagaId, event.causationId(), event.createdAt(),
                new PurchaseReservationReleasedDataV1(sagaId, uuid(payload, "orderId"),
                        uuid(payload, "purchaseRequestId"), reservationId, status, text(payload, "reason"),
                        instant(payload, "releasedAt")));
    }

    private UUID uuid(Map<String, Object> payload, String field) { return UUID.fromString(text(payload, field)); }
    private Instant instant(Map<String, Object> payload, String field) { return Instant.parse(text(payload, field)); }
    private String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("outbox payload field is missing: " + field);
        }
        return value.toString();
    }
}
