package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationDataV1;
import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Keeps Avro envelope, identity, and fingerprint validation at the inbound adapter boundary. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class ConfirmReservationAvroMapper {
    private final String expectedTopic;

    @Autowired
    public ConfirmReservationAvroMapper(ReservationCommandProperties properties) {
        this(properties.topic());
    }

    ConfirmReservationAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("reservation command topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public ConfirmReservationCommand map(ConsumerRecord<String, ConfirmPurchaseReservationV1> record) {
        if (record == null || !expectedTopic.equals(record.topic()) || record.value() == null) {
            throw invalid("record or topic is invalid");
        }
        ConfirmPurchaseReservationV1 event = record.value();
        ConfirmPurchaseReservationDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getAggregateId() == null
                || event.getCorrelationId() == null || event.getCausationId() == null
                || event.getOccurredAt() == null) {
            throw invalid("envelope or data is missing");
        }
        require(event.getEventType(), "ConfirmPurchaseReservation", "eventType");
        require(event.getProducer(), "order-service", "producer");
        require(event.getAggregateType(), "PURCHASE_SAGA", "aggregateType");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) {
            throw invalid("unsupported event or aggregate version");
        }
        UUID key = parseUuid(record.key(), "message key");
        if (!key.equals(data.getOrderId())) throw invalid("message key must equal data.orderId");
        UUID sagaId = require(data.getSagaId(), "sagaId");
        if (!sagaId.equals(event.getAggregateId())) throw invalid("aggregateId must equal sagaId");
        return new ConfirmReservationCommand(event.getEventId(), event.getEventType(), event.getEventVersion(),
                event.getProducer(), event.getAggregateType(), sagaId, event.getAggregateVersion(),
                event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(), data.getOrderId(),
                data.getPurchaseRequestId(), data.getReservationId(), data.getPaymentId(), data.getPaidAt(),
                record.topic(), record.partition(), record.offset(), header(record, "traceparent"),
                header(record, "tracestate"), fingerprint(event, data));
    }

    private String fingerprint(ConfirmPurchaseReservationV1 event, ConfirmPurchaseReservationDataV1 data) {
        String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()),
                event.getProducer(), event.getAggregateType(), event.getAggregateId().toString(),
                String.valueOf(event.getAggregateVersion()), event.getCorrelationId().toString(),
                event.getCausationId().toString(), event.getOccurredAt().toString(), data.getSagaId().toString(),
                data.getOrderId().toString(), data.getPurchaseRequestId().toString(),
                data.getReservationId().toString(), data.getPaymentId().toString(), data.getPaidAt().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot fingerprint ConfirmPurchaseReservation", exception);
        }
    }

    private String header(ConsumerRecord<String, ConfirmPurchaseReservationV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is missing");
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) { throw invalid(field + " is not a UUID"); }
    }

    private <T> T require(T value, String field) {
        if (value == null) throw invalid(field + " is missing");
        return value;
    }

    private void require(String actual, String expected, String field) {
        if (!expected.equals(actual)) throw invalid(field + " is unsupported");
    }

    private ConfirmReservationRecordException invalid(String message) {
        return new ConfirmReservationRecordException(message);
    }
}
