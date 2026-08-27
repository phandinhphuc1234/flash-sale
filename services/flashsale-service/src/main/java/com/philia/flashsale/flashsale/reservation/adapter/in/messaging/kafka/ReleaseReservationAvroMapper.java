package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationDataV1;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka.ReservationCommandProperties;
import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public final class ReleaseReservationAvroMapper {
    private final String expectedTopic;
    public ReleaseReservationAvroMapper(ReservationCommandProperties properties) { this.expectedTopic = properties.topic(); }
    public ReleaseReservationCommand map(ConsumerRecord<String, ReleasePurchaseReservationV1> record) {
        if (record == null || !expectedTopic.equals(record.topic()) || record.value() == null) throw invalid("record or topic is invalid");
        ReleasePurchaseReservationV1 event = record.value(); ReleasePurchaseReservationDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getAggregateId() == null || event.getCorrelationId() == null || event.getCausationId() == null || event.getOccurredAt() == null) throw invalid("envelope or data is missing");
        require(event.getEventType(), "ReleasePurchaseReservation", "eventType"); require(event.getProducer(), "order-service", "producer"); require(event.getAggregateType(), "PURCHASE_SAGA", "aggregateType");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) throw invalid("unsupported event or aggregate version");
        UUID key = parse(record.key(), "message key");
        if (!key.equals(data.getOrderId())) {
            throw invalid("message key must equal data.orderId");
        }
        UUID sagaId = data.getSagaId();
        if (sagaId == null || !sagaId.equals(event.getAggregateId())) {
            throw invalid("aggregateId must equal sagaId");
        }
        if (!ReleaseReservationCommand.REASONS.contains(data.getReason())) throw invalid("unsupported release reason");
        return new ReleaseReservationCommand(event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(), event.getAggregateType(),
                sagaId, event.getAggregateVersion(), event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(), data.getOrderId(), data.getPurchaseRequestId(), data.getReservationId(), data.getReason(), record.topic(), record.partition(), record.offset(), header(record, "traceparent"), header(record, "tracestate"), fingerprint(event, data));
    }
    private String fingerprint(ReleasePurchaseReservationV1 event, ReleasePurchaseReservationDataV1 data) { String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()), event.getProducer(), event.getAggregateType(), event.getAggregateId().toString(), String.valueOf(event.getAggregateVersion()), event.getCorrelationId().toString(), event.getCausationId().toString(), event.getOccurredAt().toString(), data.getSagaId().toString(), data.getOrderId().toString(), data.getPurchaseRequestId().toString(), data.getReservationId().toString(), data.getReason()); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("cannot fingerprint ReleasePurchaseReservation", e); } }
    private void require(String actual, String expected, String field) { if (!expected.equals(actual)) throw invalid(field + " is unsupported"); }
    private UUID parse(String value, String field) { try { if (value == null) throw new IllegalArgumentException(); return UUID.fromString(value); } catch (Exception e) { throw invalid(field + " is not a UUID"); } }
    private String header(ConsumerRecord<String, ReleasePurchaseReservationV1> record, String name) { var h = record.headers().lastHeader(name); return h == null ? null : new String(h.value(), StandardCharsets.UTF_8); }
    private ReleaseReservationRecordException invalid(String message) { return new ReleaseReservationRecordException(message); }
}
