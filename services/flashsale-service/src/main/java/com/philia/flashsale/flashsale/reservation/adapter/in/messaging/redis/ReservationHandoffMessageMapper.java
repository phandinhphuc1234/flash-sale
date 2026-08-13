package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.MapRecord;

/** Maps the immutable Redis Stream command into the application snapshot boundary. */
public final class ReservationHandoffMessageMapper {
    public AcceptedReservationSnapshot map(MapRecord<String, String, String> record) {
        Objects.requireNonNull(record, "record");
        Map<String, String> values = record.getValue();
        return new AcceptedReservationSnapshot(
                uuid(values, "purchaseRequestId"),
                uuid(values, "reservationId"),
                uuid(values, "eventId"),
                uuid(values, "campaignId"),
                uuid(values, "variantId"),
                uuid(values, "userId"),
                uuid(values, "inventoryAllocationId"),
                required(values, "skuSnapshot"),
                new BigDecimal(required(values, "unitPrice")),
                required(values, "currency"),
                number(values, "quantity"),
                required(values, "requestHash"),
                required(values, "idempotencyKeyHash"),
                instant(values, "acceptedAt"),
                instant(values, "expiresAt"),
                instant(values, "retainedUntil"),
                optional(values, "traceparent"),
                optional(values, "tracestate"));
    }

    private static UUID uuid(Map<String, String> values, String field) {
        return UUID.fromString(required(values, field));
    }

    private static long number(Map<String, String> values, String field) {
        return Long.parseLong(required(values, field));
    }

    private static Instant instant(Map<String, String> values, String field) {
        return Instant.ofEpochMilli(number(values, field));
    }

    private static String required(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("handoff field is missing: " + field);
        }
        return value;
    }

    private static String optional(Map<String, String> values, String field) {
        return values.getOrDefault(field, "");
    }
}
