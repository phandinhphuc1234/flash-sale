package com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps the internal JSON snapshot to the versioned Avro wire contract. */
@Component
public final class PurchaseAcceptedAvroMapper {
    public PurchaseAcceptedV1 map(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        Map<String, Object> payload = event.payload();
        UUID purchaseRequestId = uuid(payload, "purchaseRequestId");
        PurchaseAcceptedDataV1 data = new PurchaseAcceptedDataV1(
                purchaseRequestId,
                uuid(payload, "reservationId"),
                uuid(payload, "campaignId"),
                uuid(payload, "variantId"),
                uuid(payload, "userId"),
                number(payload, "quantity"),
                new BigDecimal(text(payload, "unitPrice")).setScale(4),
                text(payload, "currency"),
                instant(payload, "acceptedAt"),
                instant(payload, "expiresAt"));
        return new PurchaseAcceptedV1(
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                "flashsale-service",
                event.aggregateType(),
                event.aggregateId(),
                event.aggregateVersion(),
                purchaseRequestId,
                null,
                event.createdAt(),
                data);
    }

    private UUID uuid(Map<String, Object> payload, String field) {
        return UUID.fromString(text(payload, field));
    }

    private long number(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(text(payload, field));
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
