package com.philia.flashsale.inventory.regularhold.adapter.out.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredV1;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactOutboxEvent;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldOutboxEvent;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

/** Reconstitutes only known Inventory facts as exact SpecificRecord Avro messages. */
@Component
public class RegularHoldOutcomeAvroMapper {
    private final ObjectMapper objectMapper;

    public RegularHoldOutcomeAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SpecificRecord toRecord(RegularHoldOutboxEvent outbox) {
        RegularHoldFactOutboxEvent event = payload(outbox);
        return switch (event.eventType()) {
            case "RegularStockHoldConfirmed" -> confirmed(event);
            case "RegularStockHoldExpired" -> expired(event);
            default -> throw new IllegalArgumentException("Unsupported regular hold outbox event " + event.eventType());
        };
    }

    private RegularStockHoldConfirmedV1 confirmed(RegularHoldFactOutboxEvent event) {
        if (event.paymentId() == null) {
            throw new IllegalArgumentException("Confirmed regular hold event requires paymentId");
        }
        var data = new RegularStockHoldConfirmedDataV1(event.holdId(), event.purchaseRequestId(), event.orderId(),
                "CONFIRMED", event.items().stream()
                        .map(item -> new RegularStockHoldConfirmedItemV1(item.variantId(), item.quantity())).toList(),
                event.paymentId(), event.transitionedAt());
        return new RegularStockHoldConfirmedV1(event.eventId(), "RegularStockHoldConfirmed", 1,
                "inventory-service", "REGULAR_STOCK_HOLD", event.holdId(), event.aggregateVersion(),
                event.purchaseRequestId(), event.causationId(), event.transitionedAt(), event.traceparent(),
                event.tracestate(), data);
    }

    private RegularStockHoldExpiredV1 expired(RegularHoldFactOutboxEvent event) {
        var data = new RegularStockHoldExpiredDataV1(event.holdId(), event.purchaseRequestId(), event.orderId(),
                "EXPIRED", event.items().stream()
                        .map(item -> new RegularStockHoldExpiredItemV1(item.variantId(), item.quantity())).toList(),
                event.transitionedAt());
        return new RegularStockHoldExpiredV1(event.eventId(), "RegularStockHoldExpired", 1,
                "inventory-service", "REGULAR_STOCK_HOLD", event.holdId(), event.aggregateVersion(),
                event.purchaseRequestId(), event.causationId(), event.transitionedAt(), event.traceparent(),
                event.tracestate(), data);
    }

    private RegularHoldFactOutboxEvent payload(RegularHoldOutboxEvent outbox) {
        try {
            RegularHoldFactOutboxEvent event = objectMapper.readValue(outbox.payload(), RegularHoldFactOutboxEvent.class);
            if (!Objects.equals(outbox.eventId(), event.eventId()) || !Objects.equals(outbox.holdId(), event.holdId())
                    || !Objects.equals(outbox.eventType(), event.eventType())) {
                throw new IllegalArgumentException("Regular hold outbox envelope does not match its payload");
            }
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Regular hold outbox payload is invalid", exception);
        }
    }
}
