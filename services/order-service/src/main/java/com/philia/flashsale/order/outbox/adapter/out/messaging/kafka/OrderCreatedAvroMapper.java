package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedDataV1;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedItemV1;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Maps the immutable outbox snapshot to the approved OrderCreatedV1 wire contract. */
public final class OrderCreatedAvroMapper {
    private static final String EVENT_TYPE = "OrderCreated";
    private static final String PRODUCER = "order-service";
    private static final String AGGREGATE_TYPE = "ORDER";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "authorization", "jwt", "password", "secret", "providerCredentials", "kafkaOffset",
            "outboxStatus", "attemptCount", "claimedBy", "claimUntil", "publishedAt", "lastError",
            "rowVersion", "sqlError");

    private final ObjectMapper objectMapper;

    public OrderCreatedAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public OrderCreatedV1 map(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        validateEnvelope(event);
        JsonNode payload = parsePayload(event.payload());
        UUID aggregateId = uuid(payload, "orderId");
        if (!aggregateId.equals(event.aggregateId()) || !event.eventKey().equals(aggregateId.toString())) {
            throw invalid("outbox aggregate and event key must match payload orderId");
        }
        rejectForbiddenFields(payload);
        String orderNumber = boundedText(payload, "orderNumber", 64);
        String status = text(payload, "status");
        if (!"PENDING_PAYMENT".equals(status)) {
            throw invalid("OrderCreated.v1 status must be PENDING_PAYMENT");
        }
        String currency = text(payload, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw invalid("OrderCreated.v1 currency must be three uppercase letters");
        }
        BigDecimal subtotal = money(payload, "subtotalAmount");
        BigDecimal total = money(payload, "totalAmount");
        Instant acceptedAt = instant(payload, "acceptedAt");
        Instant reservationExpiresAt = instant(payload, "reservationExpiresAt");
        if (!acceptedAt.isBefore(reservationExpiresAt)) {
            throw invalid("OrderCreated.v1 acceptedAt must be before reservationExpiresAt");
        }
        OrderCreatedItemV1 item = item(payload);
        BigDecimal expectedLineAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        if (item.getLineAmount().compareTo(expectedLineAmount) != 0
                || subtotal.compareTo(item.getLineAmount()) != 0
                || total.compareTo(subtotal) != 0) {
            throw invalid("OrderCreated.v1 amounts do not match the single item");
        }
        OrderCreatedDataV1 data = new OrderCreatedDataV1(
                aggregateId,
                orderNumber,
                uuid(payload, "purchaseRequestId"),
                uuid(payload, "reservationId"),
                uuid(payload, "campaignId"),
                uuid(payload, "userId"),
                status,
                currency,
                subtotal,
                total,
                acceptedAt,
                reservationExpiresAt,
                List.of(item));
        return new OrderCreatedV1(event.eventId(), event.eventType(), event.eventVersion(), PRODUCER,
                event.aggregateType(), event.aggregateId(), event.aggregateVersion(), event.correlationId(),
                event.causationId(), event.occurredAt(), data);
    }

    private void validateEnvelope(OrderOutboxEvent event) {
        if (!EVENT_TYPE.equals(event.eventType()) || event.eventVersion() != 1
                || !AGGREGATE_TYPE.equals(event.aggregateType())
                || event.aggregateVersion() != 1) {
            throw invalid("outbox envelope is not OrderCreated.v1");
        }
    }

    private JsonNode parsePayload(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw invalid("outbox payload must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("outbox payload cannot be decoded", exception);
        }
    }

    private OrderCreatedItemV1 item(JsonNode payload) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.size() != 1) {
            throw invalid("OrderCreated.v1 must contain exactly one item");
        }
        JsonNode value = items.get(0);
        if (value == null || !value.isObject()) {
            throw invalid("OrderCreated.v1 item must be an object");
        }
        return new OrderCreatedItemV1(uuid(value, "variantId"), positiveQuantity(value, "quantity"),
                money(value, "unitPrice"), money(value, "lineAmount"));
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException exception) {
            throw invalid("outbox field is not a UUID: " + field);
        }
    }

    private long positiveQuantity(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw invalid("outbox quantity must be positive: " + field);
        }
        return value.asLong();
    }

    private BigDecimal money(JsonNode node, String field) {
        try {
            BigDecimal value = new BigDecimal(text(node, field)).setScale(4, RoundingMode.UNNECESSARY);
            if (value.signum() <= 0 || value.precision() > 19) {
                throw invalid("outbox money is outside NUMERIC(19,4): " + field);
            }
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid("outbox money has invalid scale: " + field);
        }
    }

    private Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(text(node, field));
        } catch (RuntimeException exception) {
            throw invalid("outbox timestamp is invalid: " + field);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("outbox field is missing: " + field);
        }
        return value.asText();
    }

    private String boundedText(JsonNode node, String field, int maxLength) {
        String value = text(node, field);
        if (value.length() > maxLength) {
            throw invalid("outbox field exceeds maximum length: " + field);
        }
        return value;
    }

    private void rejectForbiddenFields(JsonNode payload) {
        Set<String> present = new HashSet<>();
        payload.fieldNames().forEachRemaining(present::add);
        present.retainAll(FORBIDDEN_FIELDS);
        if (!present.isEmpty()) {
            throw invalid("OrderCreated.v1 contains forbidden internal fields");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
