package com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;

/** Maps the safe JSON snapshot to the exact generated Payment result contract at the Kafka edge. */
public final class PaymentResultAvroMapper {

    private static final Set<String> FORBIDDEN_FIELD_FRAGMENTS = Set.of(
            "pan", "cvv", "cvc", "card", "secret", "token", "password", "signature", "rawbody", "url");
    private final ObjectMapper objectMapper;

    public PaymentResultAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SpecificRecord toRecord(PaymentOutboxEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("outbox event is required");
        }
        JsonNode root = read(event.payload());
        assertNoForbiddenFields(root);
        Base base = base(event, root);
        return switch (event.eventType()) {
            case "PaymentSucceeded" -> succeeded(event, root, base);
            case "PaymentFailed" -> failed(event, root, base);
            default -> throw new IllegalArgumentException("unsupported Payment event type: " + event.eventType());
        };
    }

    private PaymentSucceededV1 succeeded(PaymentOutboxEvent event, JsonNode root, Base base) {
        JsonNode data = requiredObject(root, "data");
        UUID paymentId = uuid(data, "paymentId");
        UUID orderId = uuid(data, "orderId");
        String sessionId = text(data, "providerSessionId", true);
        PaymentSucceededDataV1 dataRecord = new PaymentSucceededDataV1(
                paymentId, orderId, amount(data), text(data, "currency", true),
                instant(data, "paidAt"), text(data, "provider", true), sessionId,
                nullableText(data, "providerPaymentIntentId"));
        assertOrderKey(event, orderId);
        return new PaymentSucceededV1(base.eventId(), "PaymentSucceeded", 1, "payment-service", "PAYMENT",
                base.aggregateId(), base.aggregateVersion(), base.correlationId(), base.causationId(),
                base.occurredAt(), dataRecord);
    }

    private PaymentFailedV1 failed(PaymentOutboxEvent event, JsonNode root, Base base) {
        JsonNode data = requiredObject(root, "data");
        UUID paymentId = uuid(data, "paymentId");
        UUID orderId = uuid(data, "orderId");
        PaymentFailedDataV1 dataRecord = new PaymentFailedDataV1(
                paymentId, orderId, amount(data), text(data, "currency", true),
                instant(data, "failedAt"), text(data, "reason", true), text(data, "provider", true),
                nullableText(data, "providerSessionId"));
        assertOrderKey(event, orderId);
        return new PaymentFailedV1(base.eventId(), "PaymentFailed", 1, "payment-service", "PAYMENT",
                base.aggregateId(), base.aggregateVersion(), base.correlationId(), base.causationId(),
                base.occurredAt(), dataRecord);
    }

    private Base base(PaymentOutboxEvent event, JsonNode root) {
        UUID eventId = uuid(root, "eventId");
        if (!event.eventId().equals(eventId)) {
            throw new IllegalArgumentException("outbox eventId does not match payload eventId");
        }
        if (!event.eventType().equals(text(root, "eventType", true))
                || event.eventVersion() != integer(root, "eventVersion")) {
            throw new IllegalArgumentException("outbox event type/version does not match payload");
        }
        if (!"payment-service".equals(text(root, "producer", true))
                || !"PAYMENT".equals(text(root, "aggregateType", true))) {
            throw new IllegalArgumentException("Payment result envelope has invalid producer or aggregate type");
        }
        UUID aggregateId = uuid(root, "aggregateId");
        long aggregateVersion = number(root, "aggregateVersion");
        if (!event.aggregateId().equals(aggregateId)
                || event.aggregateVersion() != aggregateVersion) {
            throw new IllegalArgumentException("outbox aggregate identity/version does not match payload");
        }
        return new Base(eventId, aggregateId, aggregateVersion,
                uuid(root, "correlationId"), uuid(root, "causationId"), instant(root, "occurredAt"));
    }

    private void assertOrderKey(PaymentOutboxEvent event, UUID orderId) {
        if (!event.messageKey().equals(orderId)) {
            throw new IllegalArgumentException("Payment result Kafka key must equal data.orderId");
        }
    }

    private BigDecimal amount(JsonNode data) {
        JsonNode value = data.get("amount");
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("Payment result amount must be numeric");
        }
        BigDecimal amount = value.decimalValue();
        if (amount.signum() <= 0 || amount.scale() > 4 || amount.precision() > 19) {
            throw new IllegalArgumentException("Payment result amount has invalid precision or scale");
        }
        return amount.setScale(4, RoundingMode.UNNECESSARY);
    }

    private JsonNode read(String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Payment outbox payload is not valid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Payment outbox payload must be a JSON object");
        }
        return root;
    }

    private void assertNoForbiddenFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String fieldName = names.next();
                String name = fieldName.toLowerCase(Locale.ROOT).replace("_", "");
                if (FORBIDDEN_FIELD_FRAGMENTS.stream().anyMatch(name::contains)) {
                    throw new IllegalArgumentException("Payment result contains a forbidden field");
                }
                assertNoForbiddenFields(node.get(fieldName));
            }
        } else if (node.isArray()) {
            node.forEach(this::assertNoForbiddenFields);
        }
    }

    private JsonNode requiredObject(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Payment result field " + name + " must be an object");
        }
        return node;
    }

    private String text(JsonNode parent, String name, boolean required) {
        JsonNode node = parent.get(name);
        if (node == null || !node.isTextual() || (required && node.textValue().isBlank())) {
            throw new IllegalArgumentException("Payment result field " + name + " must be text");
        }
        return node.textValue();
    }

    private String nullableText(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return null;
        }
        return text(parent, name, true);
    }

    private UUID uuid(JsonNode parent, String name) {
        try {
            return UUID.fromString(text(parent, name, true));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Payment result field " + name + " must be a UUID", exception);
        }
    }

    private Instant instant(JsonNode parent, String name) {
        try {
            return Instant.parse(text(parent, name, true));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Payment result field " + name + " must be an instant", exception);
        }
    }

    private int integer(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || !node.canConvertToInt()) {
            throw new IllegalArgumentException("Payment result field " + name + " must be an integer");
        }
        return node.intValue();
    }

    private long number(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || !node.canConvertToLong()) {
            throw new IllegalArgumentException("Payment result field " + name + " must be a number");
        }
        return node.longValue();
    }

    private record Base(UUID eventId, UUID aggregateId, long aggregateVersion,
            UUID correlationId, UUID causationId, Instant occurredAt) {
    }
}
