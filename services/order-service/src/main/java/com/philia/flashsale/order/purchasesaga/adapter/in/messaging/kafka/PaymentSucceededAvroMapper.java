package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.order.configuration.OrderKafkaProperties;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/** Keeps Avro/Kafka validation at the inbound adapter boundary. */
@Component
public final class PaymentSucceededAvroMapper {
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private final String expectedTopic;

    public PaymentSucceededAvroMapper(OrderKafkaProperties properties) {
        this(properties.paymentEventsTopic());
    }

    PaymentSucceededAvroMapper(String expectedTopic) {
        if (expectedTopic == null || expectedTopic.isBlank()) {
            throw new IllegalArgumentException("payment events topic must not be blank");
        }
        this.expectedTopic = expectedTopic;
    }

    public PaymentSucceededCommand map(ConsumerRecord<String, PaymentSucceededV1> record) {
        if (record == null || !expectedTopic.equals(record.topic()) || !(record.value() instanceof PaymentSucceededV1 event)) {
            throw invalid("record or topic is invalid");
        }
        PaymentSucceededDataV1 data = event.getData();
        if (data == null || event.getEventId() == null || event.getCorrelationId() == null
                || event.getCausationId() == null || event.getOccurredAt() == null) {
            throw invalid("envelope or data is missing");
        }
        requireText(event.getEventType(), "PaymentSucceeded", "eventType");
        requireText(event.getProducer(), "payment-service", "producer");
        requireText(event.getAggregateType(), "PAYMENT", "aggregateType");
        if (event.getEventVersion() != 1 || event.getAggregateVersion() <= 0) {
            throw invalid("unsupported event or aggregate version");
        }
        require(data.getPaymentId(), "paymentId");
        require(data.getOrderId(), "orderId");
        UUID key = parseUuid(record.key(), "message key");
        if (!key.equals(data.getOrderId())) {
            throw invalid("message key must equal data.orderId");
        }
        if (!key.equals(event.getCorrelationId()) && !key.equals(event.getAggregateId())) {
            // Correlation is normally the Order identity; aggregateId remains the Payment identity.
            throw invalid("event identity does not reference the Order key");
        }
        BigDecimal amount = normalizeAmount(data.getAmount());
        if (data.getCurrency() == null || !CURRENCY.matcher(data.getCurrency()).matches()) {
            throw invalid("currency must be three uppercase letters");
        }
        require(data.getPaidAt(), "paidAt");
        requireTextNonBlank(data.getProvider(), "provider");
        requireTextNonBlank(data.getProviderSessionId(), "providerSessionId");
        return new PaymentSucceededCommand(event.getEventId(), event.getEventType(), event.getEventVersion(),
                event.getProducer(), event.getAggregateType(), event.getAggregateId(), event.getAggregateVersion(),
                event.getCorrelationId(), event.getCausationId(), event.getOccurredAt(), data.getPaymentId(),
                data.getOrderId(), amount, data.getCurrency(), data.getPaidAt(), data.getProvider(),
                data.getProviderSessionId(), data.getProviderPaymentIntentId(), record.topic(), record.partition(),
                record.offset(), header(record, "traceparent"), header(record, "tracestate"), fingerprint(event, data));
    }

    private String fingerprint(PaymentSucceededV1 event, PaymentSucceededDataV1 data) {
        String canonical = String.join("|", event.getEventType(), String.valueOf(event.getEventVersion()),
                event.getProducer(), event.getAggregateType(), event.getAggregateId().toString(),
                String.valueOf(event.getAggregateVersion()), event.getCorrelationId().toString(),
                event.getCausationId().toString(), event.getOccurredAt().toString(), data.getPaymentId().toString(),
                data.getOrderId().toString(), normalizeAmount(data.getAmount()).toPlainString(), data.getCurrency(),
                data.getPaidAt().toString(), data.getProvider(), data.getProviderSessionId(),
                String.valueOf(data.getProviderPaymentIntentId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot fingerprint PaymentSucceeded", exception);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) throw invalid("amount is missing");
        try {
            BigDecimal amount = value.setScale(4, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.precision() > 19) throw invalid("amount is invalid");
            return amount;
        } catch (ArithmeticException exception) {
            throw new PaymentSucceededRecordException("amount has invalid scale");
        }
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

    private void requireText(String actual, String expected, String field) {
        if (!expected.equals(actual)) throw invalid(field + " is unsupported");
    }

    private void requireTextNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is missing");
    }

    private String header(ConsumerRecord<String, PaymentSucceededV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private PaymentSucceededRecordException invalid(String message) {
        return new PaymentSucceededRecordException(message);
    }
}
