package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Validates the versioned Flash Sale release-result boundary before entering the core. */
@Component
public final class PurchaseReservationReleasedAvroMapper {
    private final String expectedTopic;
    public PurchaseReservationReleasedAvroMapper(OrderKafkaProperties properties) { this.expectedTopic = properties.purchaseReservationResultsTopic(); }
    public PurchaseReservationReleasedCommand map(ConsumerRecord<String, PurchaseReservationReleasedV1> record) {
        if (record == null || !expectedTopic.equals(record.topic()) || !(record.value() instanceof PurchaseReservationReleasedV1 event)) throw invalid("record or topic is invalid");
        PurchaseReservationReleasedDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getAggregateId() == null || event.getCorrelationId() == null || event.getCausationId() == null || event.getOccurredAt() == null || data.getReleasedAt() == null) throw invalid("envelope or data is missing");
        require(event.getEventType(), "PurchaseReservationReleased", "eventType"); require(event.getProducer(), "flashsale-service", "producer"); require(event.getAggregateType(), "PURCHASE_RESERVATION", "aggregateType");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) throw invalid("unsupported event or aggregate version");
        UUID key = parse(record.key(), "message key"); if (!key.equals(data.getOrderId())) throw invalid("message key must equal data.orderId");
        if (!event.getAggregateId().equals(data.getReservationId())) throw invalid("aggregateId must equal data.reservationId");
        if (!event.getCorrelationId().equals(data.getSagaId()) || !data.getSagaId().equals(data.getPurchaseRequestId())) throw invalid("Saga identity does not match correlation or purchase request");
        if (!PurchaseReservationReleasedCommand.STATUSES.contains(data.getReservationStatus())) throw invalid("unsupported reservation status");
        return new PurchaseReservationReleasedCommand(event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(), event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(), event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(), data.getSagaId(), data.getOrderId(), data.getPurchaseRequestId(), data.getReservationId(), data.getReservationStatus(), data.getReason(), data.getReleasedAt(), record.topic(), record.partition(), record.offset(), header(record, "traceparent"), header(record, "tracestate"), fingerprint(event, data));
    }
    private String fingerprint(PurchaseReservationReleasedV1 event, PurchaseReservationReleasedDataV1 data) {
        String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()), event.getProducer(), event.getAggregateType(), event.getAggregateId().toString(), String.valueOf(event.getAggregateVersion()), event.getCorrelationId().toString(), event.getCausationId().toString(), event.getOccurredAt().toString(), data.getSagaId().toString(), data.getOrderId().toString(), data.getPurchaseRequestId().toString(), data.getReservationId().toString(), data.getReservationStatus(), data.getReason(), data.getReleasedAt().toString());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException("cannot fingerprint PurchaseReservationReleased", exception); }
    }
    private void require(String actual, String expected, String field) { if (!expected.equals(actual)) throw invalid(field + " is unsupported"); }
    private UUID parse(String value, String field) { try { return UUID.fromString(value); } catch (Exception exception) { throw invalid(field + " is not a UUID"); } }
    private String header(ConsumerRecord<?, ?> record, String name) { var header = record.headers().lastHeader(name); return header == null ? null : new String(header.value(), StandardCharsets.UTF_8); }
    private PurchaseReservationReleasedRecordException invalid(String message) { return new PurchaseReservationReleasedRecordException(message); }
}
