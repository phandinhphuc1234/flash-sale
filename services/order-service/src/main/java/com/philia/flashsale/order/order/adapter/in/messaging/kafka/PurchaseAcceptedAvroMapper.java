package com.philia.flashsale.order.order.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedDataV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converts the generated wire record into the framework-free Order command.
 *
 * <p>All envelope, identity, money, and temporal checks live at this boundary so Avro and Kafka
 * types cannot leak into the application core.</p>
 */
@Component
public final class PurchaseAcceptedAvroMapper {

    private static final String EVENT_TYPE = "PurchaseAccepted";
    private static final String PRODUCER = "flashsale-service";
    private static final String AGGREGATE_TYPE = "PURCHASE_REQUEST";
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Duration ACCEPTANCE_TTL = Duration.ofMinutes(5);

    private final String expectedTopic;

    @Autowired
    public PurchaseAcceptedAvroMapper(OrderKafkaProperties properties) {
        this(properties.acceptedPurchaseTopic());
    }

    public PurchaseAcceptedAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("accepted purchase topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public CreateOrderFromAcceptedPurchaseCommand map(ConsumerRecord<String, PurchaseAcceptedV1> record) {
        if (record == null) {
            throw invalid("record is null");
        }
        if (!expectedTopic.equals(record.topic())) {
            throw invalid("unexpected topic");
        }
        Object rawValue = record.value();
        if (!(rawValue instanceof PurchaseAcceptedV1 event)) {
            throw invalid("unsupported or null PurchaseAccepted value");
        }
        PurchaseAcceptedDataV1 data = event.getData();
        if (data == null) {
            throw invalid("data is missing");
        }

        validateEnvelope(event, record.key(), data);
        validateBusiness(data, event.getOccurredAt());
        return new CreateOrderFromAcceptedPurchaseCommand(
                event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(),
                event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(),
                data.getPurchaseRequestId(), data.getReservationId(), data.getCampaignId(),
                data.getVariantId(), data.getUserId(), data.getQuantity(), normalizeMoney(data.getUnitPrice()),
                data.getCurrency(), data.getAcceptedAt(), data.getExpiresAt(), record.topic(), record.partition(),
                record.offset(), header(record, "traceparent"), header(record, "tracestate"));
    }

    private void validateEnvelope(PurchaseAcceptedV1 event, String key, PurchaseAcceptedDataV1 data) {
        require(event.getEventId(), "eventId");
        requireText(event.getEventType(), EVENT_TYPE, "eventType");
        if (event.getEventVersion() != 1) {
            throw invalid("eventVersion must be 1");
        }
        requireText(event.getProducer(), PRODUCER, "producer");
        requireText(event.getAggregateType(), AGGREGATE_TYPE, "aggregateType");
        require(event.getAggregateId(), "aggregateId");
        if (event.getAggregateVersion() != 1) {
            throw invalid("aggregateVersion must be 1");
        }
        require(event.getCorrelationId(), "correlationId");
        require(event.getOccurredAt(), "occurredAt");
        require(data.getPurchaseRequestId(), "purchaseRequestId");
        UUID keyId = parseUuid(key, "message key");
        if (!keyId.equals(data.getPurchaseRequestId()) || !keyId.equals(event.getAggregateId())) {
            throw invalid("message key, aggregateId, and purchaseRequestId must match");
        }
    }

    private void validateBusiness(PurchaseAcceptedDataV1 data, Instant occurredAt) {
        require(data.getReservationId(), "reservationId");
        require(data.getCampaignId(), "campaignId");
        require(data.getVariantId(), "variantId");
        require(data.getUserId(), "userId");
        if (data.getQuantity() <= 0) {
            throw invalid("quantity must be positive");
        }
        normalizeMoney(data.getUnitPrice());
        if (data.getCurrency() == null || !CURRENCY.matcher(data.getCurrency()).matches()) {
            throw invalid("currency must be three uppercase letters");
        }
        require(data.getAcceptedAt(), "acceptedAt");
        require(data.getExpiresAt(), "expiresAt");
        if (occurredAt.isAfter(data.getAcceptedAt())) {
            throw invalid("occurredAt must not be after acceptedAt");
        }
        if (!data.getExpiresAt().equals(data.getAcceptedAt().plus(ACCEPTANCE_TTL))) {
            throw invalid("expiresAt must be exactly five minutes after acceptedAt");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            throw invalid("unitPrice is missing");
        }
        try {
            BigDecimal normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw invalid("unitPrice must be positive NUMERIC(19,4)");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new PurchaseAcceptedRecordException("unitPrice must be representable at scale 4", exception);
        }
    }

    private UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is missing");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new PurchaseAcceptedRecordException(field + " is not a UUID", exception);
        }
    }

    private <T> T require(T value, String field) {
        if (value == null) {
            throw invalid(field + " is missing");
        }
        return value;
    }

    private void requireText(String actual, String expected, String field) {
        if (!expected.equals(actual)) {
            throw invalid(field + " is unsupported");
        }
    }

    private String header(ConsumerRecord<String, PurchaseAcceptedV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private PurchaseAcceptedRecordException invalid(String message) {
        return new PurchaseAcceptedRecordException(message);
    }
}
