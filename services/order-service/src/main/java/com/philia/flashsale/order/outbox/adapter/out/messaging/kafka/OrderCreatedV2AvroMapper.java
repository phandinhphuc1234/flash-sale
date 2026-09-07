package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v2.OrderCreatedDataV2;
import com.philia.flashsale.contract.order.event.v2.OrderCreatedItemV2;
import com.philia.flashsale.contract.order.event.v2.OrderCreatedV2;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict anti-corruption mapper for additive regular OrderCreated V2 facts. */
public final class OrderCreatedV2AvroMapper {
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "authorization", "jwt", "password", "secret", "providerCredentials", "kafkaOffset",
            "outboxStatus", "attemptCount", "claimedBy", "claimUntil", "publishedAt", "lastError",
            "rowVersion", "sqlError");

    private final ObjectMapper objectMapper;

    public OrderCreatedV2AvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public OrderCreatedV2 map(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"OrderCreatedV2".equals(event.eventType()) || event.eventVersion() != 2
                || !"ORDER".equals(event.aggregateType()) || event.aggregateVersion() < 1) {
            throw invalid("outbox envelope is not OrderCreated.v2");
        }
        JsonNode root = parse(event.payload());
        rejectForbidden(root);
        UUID orderId = uuid(root, "orderId");
        if (!event.aggregateId().equals(orderId) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderCreatedV2 aggregate and key must match orderId");
        }
        String source = oneOf(root, "purchaseSource", Set.of("BUY_NOW", "CART"));
        String participant = oneOf(root, "stockParticipantType", Set.of("REGULAR_STOCK_HOLD"));
        UUID cartId = nullableUuid(root, "cartId");
        Long cartVersion = nullableNonNegativeLong(root, "cartVersion");
        if (("CART".equals(source)) != (cartId != null && cartVersion != null)) {
            throw invalid("Cart identity must be present only for CART source");
        }
        Instant acceptedAt = instant(root, "acceptedAt");
        Instant holdExpiresAt = instant(root, "stockHoldExpiresAt");
        Instant paymentDeadline = instant(root, "paymentDeadline");
        if (!acceptedAt.isBefore(holdExpiresAt) || !paymentDeadline.equals(holdExpiresAt.minusSeconds(30))) {
            throw invalid("regular OrderCreatedV2 deadline is invalid");
        }
        List<OrderCreatedItemV2> items = items(root);
        BigDecimal subtotal = money(root, "subtotalAmount");
        BigDecimal total = money(root, "totalAmount");
        BigDecimal summed = items.stream().map(OrderCreatedItemV2::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.compareTo(summed) != 0 || total.compareTo(subtotal) != 0) {
            throw invalid("regular OrderCreatedV2 amounts do not match items");
        }
        return new OrderCreatedV2(event.eventId(), "OrderCreated", 2, "order-service", "ORDER", orderId,
                event.aggregateVersion(), event.correlationId(), event.causationId(), event.occurredAt(),
                event.traceparent(), event.tracestate(), new OrderCreatedDataV2(orderId,
                        boundedText(root, "orderNumber", 64), uuid(root, "purchaseRequestId"),
                        uuid(root, "userId"), source, participant, uuid(root, "stockReferenceId"), cartId,
                        cartVersion, oneOf(root, "status", Set.of("PENDING_PAYMENT")),
                        oneOfCurrency(root), subtotal, total, acceptedAt, holdExpiresAt, paymentDeadline, items));
    }

    private JsonNode parse(String payload) {
        try {
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) throw invalid("outbox payload must be an object");
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OrderCreatedV2 payload cannot be decoded", exception);
        }
    }

    private List<OrderCreatedItemV2> items(JsonNode root) {
        JsonNode values = root.get("items");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 20) {
            throw invalid("OrderCreatedV2 items must contain one through 20 entries");
        }
        Set<UUID> variants = new HashSet<>();
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).map(item -> {
            if (!item.isObject()) throw invalid("OrderCreatedV2 item must be an object");
            UUID variantId = uuid(item, "variantId");
            if (!variants.add(variantId)) throw invalid("OrderCreatedV2 items must be distinct");
            long quantity = positiveQuantity(item, "quantity");
            BigDecimal unitPrice = money(item, "unitPrice");
            BigDecimal lineAmount = money(item, "lineAmount");
            if (lineAmount.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
                throw invalid("OrderCreatedV2 item amount does not match quantity");
            }
            return new OrderCreatedItemV2(variantId, quantity, unitPrice, lineAmount, null, null, null);
        }).toList();
    }

    private String oneOfCurrency(JsonNode root) {
        String currency = text(root, "currency");
        if (!currency.matches("[A-Z]{3}")) throw invalid("currency must be three uppercase letters");
        return currency;
    }

    private String oneOf(JsonNode root, String field, Set<String> allowed) {
        String value = text(root, field);
        if (!allowed.contains(value)) throw invalid("unsupported " + field);
        return value;
    }

    private UUID uuid(JsonNode root, String field) {
        try {
            return UUID.fromString(text(root, field));
        } catch (IllegalArgumentException exception) {
            throw invalid("outbox field is not a UUID: " + field);
        }
    }

    private UUID nullableUuid(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null) throw invalid("outbox field is missing: " + field);
        if (value.isNull()) return null;
        if (!value.isTextual()) throw invalid("outbox field is not a UUID: " + field);
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException exception) {
            throw invalid("outbox field is not a UUID: " + field);
        }
    }

    private Long nullableNonNegativeLong(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null) throw invalid("outbox field is missing: " + field);
        if (value.isNull()) return null;
        if (!value.canConvertToLong() || value.asLong() < 0) throw invalid("outbox field is invalid: " + field);
        return value.asLong();
    }

    private long positiveQuantity(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() < 1 || value.asLong() > 10) {
            throw invalid("outbox quantity must be one through ten");
        }
        return value.asLong();
    }

    private BigDecimal money(JsonNode root, String field) {
        try {
            BigDecimal value = new BigDecimal(text(root, field)).setScale(4, RoundingMode.UNNECESSARY);
            if (value.signum() <= 0 || value.precision() > 19) throw invalid("outbox money is invalid: " + field);
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid("outbox money has invalid scale: " + field);
        }
    }

    private Instant instant(JsonNode root, String field) {
        try {
            return Instant.parse(text(root, field));
        } catch (RuntimeException exception) {
            throw invalid("outbox timestamp is invalid: " + field);
        }
    }

    private String boundedText(JsonNode root, String field, int max) {
        String value = text(root, field);
        if (value.length() > max) throw invalid("outbox field exceeds maximum length: " + field);
        return value;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("outbox field is missing: " + field);
        }
        return value.asText();
    }

    private void rejectForbidden(JsonNode root) {
        Set<String> present = new HashSet<>();
        root.fieldNames().forEachRemaining(present::add);
        present.retainAll(FORBIDDEN_FIELDS);
        if (!present.isEmpty()) throw invalid("OrderCreatedV2 contains forbidden internal fields");
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
