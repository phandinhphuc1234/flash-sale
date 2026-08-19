package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedDataV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.payment.configuration.PaymentKafkaProperties;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Converts the generated PaymentRequested wire record into the framework-free application
 * command. All transport, envelope, key, money, and trace checks stop at this boundary.
 */
@Component
@ConditionalOnProperty(name = "payment.kafka.consumer-enabled", havingValue = "true")
public final class PaymentRequestedAvroMapper {

    private static final String EVENT_TYPE = "PaymentRequested";
    private static final String PRODUCER = "order-service";
    private static final String AGGREGATE_TYPE = "ORDER";
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern TRACEPARENT = Pattern.compile(
            "[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    private final String expectedTopic;

    @Autowired
    public PaymentRequestedAvroMapper(PaymentKafkaProperties properties) {
        this(properties.commandTopic());
    }

    /** Constructor kept explicit so mapper contract tests do not need a Spring context. */
    public PaymentRequestedAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("Payment command topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public AcceptPaymentRequestCommand map(ConsumerRecord<String, ?> record) {
        if (record == null) {
            throw invalid("record is null");
        }
        if (!expectedTopic.equals(record.topic())) {
            throw invalid("unexpected Payment command topic");
        }
        if (!(record.value() instanceof PaymentRequestedV1 event)) {
            throw invalid("unsupported or null PaymentRequested value");
        }
        PaymentRequestedDataV1 data = event.getData();
        if (data == null) {
            throw invalid("PaymentRequested data is missing");
        }

        validateEnvelope(event, data, record.key());
        validateBusiness(data);
        return new AcceptPaymentRequestCommand(
                event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(),
                event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(), data.getOrderId(),
                data.getUserId(), normalizeAmount(data.getAmount()), data.getCurrency(),
                data.getPaymentDeadline(), header(record, "traceparent"), header(record, "tracestate"));
    }

    private void validateEnvelope(PaymentRequestedV1 event, PaymentRequestedDataV1 data, String key) {
        require(event.getEventId(), "eventId");
        requireText(event.getEventType(), EVENT_TYPE, "eventType");
        if (event.getEventVersion() != 1) {
            throw invalid("eventVersion must be 1");
        }
        requireText(event.getProducer(), PRODUCER, "producer");
        requireText(event.getAggregateType(), AGGREGATE_TYPE, "aggregateType");
        require(event.getAggregateId(), "aggregateId");
        if (event.getAggregateVersion() <= 0) {
            throw invalid("aggregateVersion must be positive");
        }
        require(event.getCorrelationId(), "correlationId");
        require(event.getCausationId(), "causationId");
        require(event.getOccurredAt(), "occurredAt");
        require(data.getOrderId(), "orderId");
        UUID keyId = parseUuid(key, "message key");
        if (!keyId.equals(event.getAggregateId()) || !keyId.equals(data.getOrderId())
                || !event.getAggregateId().equals(data.getOrderId())) {
            throw invalid("message key, aggregateId, and orderId must match");
        }
    }

    private void validateBusiness(PaymentRequestedDataV1 data) {
        require(data.getUserId(), "userId");
        normalizeAmount(data.getAmount());
        if (data.getCurrency() == null || !CURRENCY.matcher(data.getCurrency()).matches()) {
            throw invalid("currency must be three uppercase ASCII letters");
        }
        require(data.getPaymentDeadline(), "paymentDeadline");
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw invalid("amount is missing");
        }
        try {
            BigDecimal normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw invalid("amount must be positive NUMERIC(19,4)");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new PaymentRequestedRecordException("amount must be representable at scale 4", exception);
        }
    }

    private UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is missing");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new PaymentRequestedRecordException(field + " is not a UUID", exception);
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

    private String header(ConsumerRecord<String, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        if ("traceparent".equals(name) && !TRACEPARENT.matcher(value).matches()) {
            throw invalid("traceparent header is malformed");
        }
        return value;
    }

    private PaymentRequestedRecordException invalid(String message) {
        return new PaymentRequestedRecordException(message);
    }
}
