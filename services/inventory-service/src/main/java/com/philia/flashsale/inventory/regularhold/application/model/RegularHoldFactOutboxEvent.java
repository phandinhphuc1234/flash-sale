package com.philia.flashsale.inventory.regularhold.application.model;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JSON-persisted, typed Inventory fact reconstructed to Avro only at the Kafka boundary. */
public record RegularHoldFactOutboxEvent(
        UUID eventId,
        String eventType,
        long aggregateVersion,
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        UUID causationId,
        String traceparent,
        String tracestate,
        RegularStockHoldStatus status,
        List<RegularHoldFactItem> items,
        UUID paymentId,
        Instant transitionedAt
) {
}
