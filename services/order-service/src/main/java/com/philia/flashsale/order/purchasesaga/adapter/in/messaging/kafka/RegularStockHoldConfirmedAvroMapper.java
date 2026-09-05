package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedDataV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedItemV1;
import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates the versioned Inventory regular-hold confirmation boundary before it enters the Order core. */
@Component
public final class RegularStockHoldConfirmedAvroMapper {
    private final String expectedTopic;

    @Autowired
    public RegularStockHoldConfirmedAvroMapper(OrderKafkaProperties properties) {
        this(properties.regularHoldEventsTopic());
    }

    RegularStockHoldConfirmedAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("regular hold events topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public RegularStockHoldConfirmedCommand map(ConsumerRecord<String, RegularStockHoldConfirmedV1> record) {
        if (record == null || !expectedTopic.equals(record.topic())
                || !(record.value() instanceof RegularStockHoldConfirmedV1 event)) {
            throw invalid("record or topic is invalid");
        }
        RegularStockHoldConfirmedDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getAggregateId() == null
                || event.getCorrelationId() == null || event.getCausationId() == null
                || event.getOccurredAt() == null || data.getHoldId() == null
                || data.getPurchaseRequestId() == null || data.getOrderId() == null
                || data.getPaymentId() == null || data.getTransitionedAt() == null || data.getItems() == null) {
            throw invalid("envelope or data is missing");
        }
        require(event.getEventType(), "RegularStockHoldConfirmed", "eventType");
        require(event.getProducer(), "inventory-service", "producer");
        require(event.getAggregateType(), "REGULAR_STOCK_HOLD", "aggregateType");
        require(data.getStatus(), "CONFIRMED", "data.status");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) {
            throw invalid("unsupported event or aggregate version");
        }
        UUID orderId = data.getOrderId();
        UUID holdId = data.getHoldId();
        UUID purchaseRequestId = data.getPurchaseRequestId();
        UUID key = parseUuid(record.key(), "message key");
        if (!key.equals(orderId) || !event.getAggregateId().equals(holdId)
                || !event.getCorrelationId().equals(purchaseRequestId)) {
            throw invalid("regular hold confirmation identity does not match key or envelope");
        }
        List<RegularStockHoldConfirmedLine> items = lines(data.getItems());
        return new RegularStockHoldConfirmedCommand(event.getEventId(), event.getEventType(), event.getEventVersion(),
                event.getProducer(), event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(),
                event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(), purchaseRequestId, orderId,
                purchaseRequestId, holdId, data.getPaymentId(), data.getTransitionedAt(), items, record.topic(),
                record.partition(), record.offset(), header(record, "traceparent"), header(record, "tracestate"),
                fingerprint(event, data, items));
    }

    private List<RegularStockHoldConfirmedLine> lines(List<RegularStockHoldConfirmedItemV1> items) {
        if (items.isEmpty() || items.size() > 20) throw invalid("regular hold confirmation items are invalid");
        try {
            List<RegularStockHoldConfirmedLine> mapped = items.stream()
                    .map(item -> new RegularStockHoldConfirmedLine(item.getVariantId(), item.getQuantity()))
                    .toList();
            if (mapped.stream().map(RegularStockHoldConfirmedLine::variantId).distinct().count() != mapped.size()) {
                throw invalid("regular hold confirmation items must be distinct");
            }
            return mapped;
        } catch (RuntimeException exception) {
            if (exception instanceof RegularStockHoldConfirmedRecordException) throw exception;
            throw invalid("regular hold confirmation items are invalid");
        }
    }

    private String fingerprint(RegularStockHoldConfirmedV1 event, RegularStockHoldConfirmedDataV1 data,
            List<RegularStockHoldConfirmedLine> items) {
        String itemFingerprint = items.stream()
                .sorted(java.util.Comparator.comparing(RegularStockHoldConfirmedLine::variantId))
                .map(item -> item.variantId() + ":" + item.quantity())
                .reduce((left, right) -> left + "," + right).orElseThrow();
        String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()),
                event.getProducer(), event.getAggregateType(), event.getAggregateId().toString(),
                String.valueOf(event.getAggregateVersion()), event.getCorrelationId().toString(),
                event.getCausationId().toString(), event.getOccurredAt().toString(), data.getHoldId().toString(),
                data.getPurchaseRequestId().toString(), data.getOrderId().toString(), data.getStatus(), itemFingerprint,
                data.getPaymentId().toString(), data.getTransitionedAt().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot fingerprint RegularStockHoldConfirmed", exception);
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

    private String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private RegularStockHoldConfirmedRecordException invalid(String message) {
        return new RegularStockHoldConfirmedRecordException(message);
    }
}
