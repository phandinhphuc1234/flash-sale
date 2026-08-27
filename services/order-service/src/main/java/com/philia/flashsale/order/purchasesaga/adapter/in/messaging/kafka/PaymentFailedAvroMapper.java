package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.payment.event.v1.PaymentFailedDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates the PaymentFailed Kafka contract before entering the Order application core. */
@Component
public final class PaymentFailedAvroMapper {
    private final String expectedTopic;

    @Autowired
    public PaymentFailedAvroMapper(OrderKafkaProperties properties) {
        this(properties.paymentEventsTopic());
    }

    PaymentFailedAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("payment topic is blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public PaymentFailedCommand map(ConsumerRecord<String, PaymentFailedV1> record) {
        if (record == null || !expectedTopic.equals(record.topic()) || record.value() == null) {
            throw invalid("record or topic is invalid");
        }
        PaymentFailedV1 event = record.value();
        PaymentFailedDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getAggregateId() == null
                || event.getCorrelationId() == null || event.getCausationId() == null || event.getOccurredAt() == null) {
            throw invalid("envelope or data is missing");
        }
        require(event.getEventType(), "PaymentFailed", "eventType");
        require(event.getProducer(), "payment-service", "producer");
        require(event.getAggregateType(), "PAYMENT", "aggregateType");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) {
            throw invalid("unsupported event or aggregate version");
        }
        UUID key = parseUuid(record.key(), "message key");
        UUID paymentId = require(data.getPaymentId(), "paymentId");
        UUID orderId = require(data.getOrderId(), "orderId");
        if (!key.equals(orderId)) {
            throw invalid("message key must equal data.orderId");
        }
        if (!key.equals(event.getCorrelationId())) {
            throw invalid("event correlation must reference order key");
        }
        if (!paymentId.equals(event.getAggregateId())) {
            throw invalid("aggregateId must equal paymentId");
        }
        BigDecimal amount = normalizeAmount(data.getAmount());
        if (data.getCurrency() == null || !data.getCurrency().matches("[A-Z]{3}")) {
            throw invalid("currency must be three uppercase letters");
        }
        require(data.getFailedAt(), "failedAt");
        requireText(data.getReason(), "reason");
        if (!PaymentFailedCommand.TERMINAL_REASONS.contains(data.getReason())) {
            throw invalid("unsupported failure reason");
        }
        requireTextNonBlank(data.getProvider(), "provider");
        return new PaymentFailedCommand(event.getEventId(), event.getEventType(), event.getEventVersion(), event.getProducer(),
                event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(), event.getCorrelationId(),
                event.getCausationId(), event.getOccurredAt(), paymentId, orderId, amount, data.getCurrency(), data.getFailedAt(),
                data.getReason(), data.getProvider(), data.getProviderSessionId(), record.topic(), record.partition(), record.offset(),
                header(record, "traceparent"), header(record, "tracestate"), fingerprint(event, data));
    }

    private String fingerprint(PaymentFailedV1 event, PaymentFailedDataV1 data) {
        String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()), event.getProducer(),
                event.getAggregateType(), event.getAggregateId().toString(), String.valueOf(event.getAggregateVersion()),
                event.getCorrelationId().toString(), event.getCausationId().toString(), event.getOccurredAt().toString(),
                data.getPaymentId().toString(), data.getOrderId().toString(), normalizeAmount(data.getAmount()).toPlainString(),
                data.getCurrency(), data.getFailedAt().toString(), data.getReason(), data.getProvider(), String.valueOf(data.getProviderSessionId()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot fingerprint PaymentFailed", exception);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            throw invalid("amount is missing");
        }
        try {
            BigDecimal normalized = value.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw invalid("amount is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw invalid("amount has invalid scale");
        }
    }

    private UUID parseUuid(String value, String field) {
        try {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(value);
        } catch (Exception exception) {
            throw invalid(field + " is not a UUID");
        }
    }

    private <T> T require(T value, String field) {
        if (value == null) {
            throw invalid(field + " is missing");
        }
        return value;
    }

    private void require(String actual, String expected, String field) {
        if (!expected.equals(actual)) {
            throw invalid(field + " is unsupported");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is missing");
        }
    }

    private void requireTextNonBlank(String value, String field) {
        requireText(value, field);
    }

    private String header(ConsumerRecord<String, PaymentFailedV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private PaymentFailedRecordException invalid(String message) {
        return new PaymentFailedRecordException(message);
    }
}
