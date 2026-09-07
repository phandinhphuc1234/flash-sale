package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldExpiredV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldReleasedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedLine;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates Inventory released/expired facts before they enter the Order core. */
@Component
public final class RegularStockHoldOutcomeAvroMapper {
    private final String expectedTopic;

    @Autowired
    public RegularStockHoldOutcomeAvroMapper(OrderKafkaProperties properties) {
        this(properties.regularHoldEventsTopic());
    }

    RegularStockHoldOutcomeAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("regular hold events topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public RegularStockHoldOutcomeCommand map(ConsumerRecord<String, Object> record) {
        if (record == null || !expectedTopic.equals(record.topic())) {
            throw invalid("record or topic is invalid");
        }
        if (record.value() instanceof RegularStockHoldReleasedV1 released) {
            return released(record, released);
        }
        if (record.value() instanceof RegularStockHoldExpiredV1 expired) {
            return expired(record, expired);
        }
        throw invalid("unsupported regular-hold result SpecificRecord");
    }

    private RegularStockHoldOutcomeCommand released(ConsumerRecord<String, Object> record,
            RegularStockHoldReleasedV1 event) {
        RegularStockHoldReleasedDataV1 data = event.getData();
        if (data == null || data.getHoldId() == null || data.getPurchaseRequestId() == null
                || data.getOrderId() == null || data.getItems() == null || data.getTransitionedAt() == null) {
            throw invalid("released envelope or data is missing");
        }
        return command(record, event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(), event.getCorrelationId(),
                event.getCausationId(), event.getOccurredAt(), data.getHoldId(), data.getPurchaseRequestId(),
                data.getOrderId(), data.getStatus(), data.getReason(), data.getTransitionedAt(), lines(data.getItems()),
                event.getTraceparent(), event.getTracestate());
    }

    private RegularStockHoldOutcomeCommand expired(ConsumerRecord<String, Object> record,
            RegularStockHoldExpiredV1 event) {
        RegularStockHoldExpiredDataV1 data = event.getData();
        if (data == null || data.getHoldId() == null || data.getPurchaseRequestId() == null
                || data.getOrderId() == null || data.getItems() == null || data.getTransitionedAt() == null) {
            throw invalid("expired envelope or data is missing");
        }
        return command(record, event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(), event.getCorrelationId(),
                event.getCausationId(), event.getOccurredAt(), data.getHoldId(), data.getPurchaseRequestId(),
                data.getOrderId(), data.getStatus(), null, data.getTransitionedAt(), lines(data.getItems()),
                event.getTraceparent(), event.getTracestate());
    }

    private RegularStockHoldOutcomeCommand command(ConsumerRecord<String, Object> record, UUID eventId,
            String eventType, int eventVersion, String producer, String aggregateType, UUID aggregateId,
            long aggregateVersion, UUID correlationId, UUID causationId, Instant occurredAt, UUID holdId,
            UUID purchaseRequestId, UUID orderId, String status, String reason, Instant transitionedAt,
            List<RegularStockHoldConfirmedLine> items, String traceparent, String tracestate) {
        if (eventId == null || eventType == null || producer == null || aggregateType == null || aggregateId == null
                || correlationId == null || causationId == null || occurredAt == null) {
            throw invalid("regular hold outcome envelope is missing");
        }
        if (!"RELEASED".equals(status) && !"EXPIRED".equals(status)) {
            throw invalid("regular hold outcome status is unsupported");
        }
        require(eventType, "RELEASED".equals(status) ? "RegularStockHoldReleased" : "RegularStockHoldExpired", "eventType");
        require(producer, "inventory-service", "producer");
        require(aggregateType, "REGULAR_STOCK_HOLD", "aggregateType");
        if (eventVersion != 1 || aggregateVersion <= 0) throw invalid("unsupported event or aggregate version");
        UUID key = parseUuid(record.key(), "message key");
        if (!key.equals(orderId) || !aggregateId.equals(holdId) || !correlationId.equals(purchaseRequestId)) {
            throw invalid("regular hold outcome identity does not match key or envelope");
        }
        String fingerprint = fingerprint(eventType, eventVersion, producer, aggregateType, aggregateId,
                aggregateVersion, correlationId, causationId, occurredAt, holdId, purchaseRequestId, orderId,
                status, reason, transitionedAt, items);
        return new RegularStockHoldOutcomeCommand(eventId, eventType, eventVersion, producer, aggregateType,
                aggregateId, aggregateVersion, correlationId, causationId, occurredAt, purchaseRequestId, orderId,
                purchaseRequestId, holdId, status, reason, transitionedAt, items, record.topic(), record.partition(),
                record.offset(), traceparent, tracestate, fingerprint);
    }

    private List<RegularStockHoldConfirmedLine> lines(List<?> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) throw invalid("regular hold outcome items are missing");
        try {
            return rawItems.stream().map(item -> {
                if (item instanceof RegularStockHoldReleasedItemV1 released) {
                    return new RegularStockHoldConfirmedLine(released.getVariantId(), released.getQuantity());
                }
                if (item instanceof RegularStockHoldExpiredItemV1 expired) {
                    return new RegularStockHoldConfirmedLine(expired.getVariantId(), expired.getQuantity());
                }
                throw invalid("regular hold outcome item type is unsupported");
            }).toList();
        } catch (RegularStockHoldOutcomeRecordException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("regular hold outcome item is malformed");
        }
    }

    private String fingerprint(String eventType, int eventVersion, String producer, String aggregateType,
            UUID aggregateId, long aggregateVersion, UUID correlationId, UUID causationId, Instant occurredAt,
            UUID holdId, UUID purchaseRequestId, UUID orderId, String status, String reason, Instant transitionedAt,
            List<RegularStockHoldConfirmedLine> items) {
        String itemFingerprint = items.stream().sorted(java.util.Comparator.comparing(RegularStockHoldConfirmedLine::variantId))
                .map(item -> item.variantId() + ":" + item.quantity()).reduce((left, right) -> left + "," + right).orElseThrow();
        String canonical = String.join("|", eventType, String.valueOf(eventVersion), producer, aggregateType,
                aggregateId.toString(), String.valueOf(aggregateVersion), correlationId.toString(), causationId.toString(),
                occurredAt.toString(), holdId.toString(), purchaseRequestId.toString(), orderId.toString(), status,
                reason == null ? "" : reason, transitionedAt.toString(), itemFingerprint);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot fingerprint regular hold outcome", exception);
        }
    }

    private UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is missing");
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) { throw invalid(field + " is not a UUID"); }
    }

    private void require(String actual, String expected, String field) {
        if (!expected.equals(actual)) throw invalid(field + " is unsupported");
    }

    private RegularStockHoldOutcomeRecordException invalid(String message) {
        return new RegularStockHoldOutcomeRecordException(message);
    }
}
