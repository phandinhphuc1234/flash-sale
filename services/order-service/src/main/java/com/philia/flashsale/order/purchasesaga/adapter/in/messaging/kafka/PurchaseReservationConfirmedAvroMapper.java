package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates the versioned Flash Sale reservation-result boundary before entering the core. */
@Component
public final class PurchaseReservationConfirmedAvroMapper {
    private final String expectedTopic;

    @Autowired
    public PurchaseReservationConfirmedAvroMapper(OrderKafkaProperties properties) {
        this(properties.purchaseReservationResultsTopic());
    }

    PurchaseReservationConfirmedAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("purchase reservation results topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public PurchaseReservationConfirmedCommand map(ConsumerRecord<String, PurchaseReservationConfirmedV1> record) {
        if (record == null || !expectedTopic.equals(record.topic())
                || !(record.value() instanceof PurchaseReservationConfirmedV1 event)) {
            throw invalid("record or topic is invalid");
        }
        PurchaseReservationConfirmedDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getAggregateId() == null
                || event.getCorrelationId() == null || event.getCausationId() == null
                || event.getOccurredAt() == null || data.getConfirmedAt() == null) {
            throw invalid("envelope or data is missing");
        }
        require(event.getEventType(), "PurchaseReservationConfirmed", "eventType");
        require(event.getProducer(), "flashsale-service", "producer");
        require(event.getAggregateType(), "PURCHASE_RESERVATION", "aggregateType");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) {
            throw invalid("unsupported event or aggregate version");
        }
        UUID key = parseUuid(record.key(), "message key");
        if (!key.equals(data.getOrderId())) throw invalid("message key must equal data.orderId");
        if (!event.getAggregateId().equals(data.getReservationId())) {
            throw invalid("aggregateId must equal data.reservationId");
        }
        if (!event.getCorrelationId().equals(data.getSagaId())
                || !data.getSagaId().equals(data.getPurchaseRequestId())) {
            throw invalid("Saga identity does not match correlation or purchase request");
        }
        return new PurchaseReservationConfirmedCommand(event.getEventId(), event.getEventType(),
                event.getEventVersion(), event.getProducer(), event.getAggregateType(), event.getAggregateId(),
                event.getAggregateVersion(), event.getCorrelationId(), event.getCausationId(),
                event.getOccurredAt(), data.getSagaId(), data.getOrderId(), data.getPurchaseRequestId(),
                data.getReservationId(), data.getPaymentId(), data.getConfirmedAt(), record.topic(),
                record.partition(), record.offset(), header(record, "traceparent"), header(record, "tracestate"),
                fingerprint(event, data));
    }

    private String fingerprint(PurchaseReservationConfirmedV1 event,
            PurchaseReservationConfirmedDataV1 data) {
        String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()),
                event.getProducer(), event.getAggregateType(), event.getAggregateId().toString(),
                String.valueOf(event.getAggregateVersion()), event.getCorrelationId().toString(),
                event.getCausationId().toString(), event.getOccurredAt().toString(), data.getSagaId().toString(),
                data.getOrderId().toString(), data.getPurchaseRequestId().toString(),
                data.getReservationId().toString(), data.getPaymentId().toString(),
                data.getConfirmedAt().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot fingerprint PurchaseReservationConfirmed", exception);
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

    private PurchaseReservationConfirmedRecordException invalid(String message) {
        return new PurchaseReservationConfirmedRecordException(message);
    }
}
