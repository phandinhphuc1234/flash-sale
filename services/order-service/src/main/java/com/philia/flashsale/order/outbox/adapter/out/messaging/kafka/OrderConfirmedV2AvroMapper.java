package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v2.OrderConfirmedDataV2;
import com.philia.flashsale.contract.order.event.v2.OrderConfirmedV2;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict anti-corruption mapper for the additive regular OrderConfirmed V2 fact. */
public final class OrderConfirmedV2AvroMapper {
    private final ObjectMapper objectMapper;

    public OrderConfirmedV2AvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public OrderConfirmedV2 map(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        if (!"OrderConfirmedV2".equals(event.eventType()) || event.eventVersion() != 2
                || !"ORDER".equals(event.aggregateType()) || event.aggregateVersion() < 1) {
            throw invalid("outbox envelope is not OrderConfirmed.v2");
        }
        JsonNode root = read(event.payload());
        UUID orderId = uuid(root, "orderId");
        if (!event.aggregateId().equals(orderId) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderConfirmedV2 aggregate and key must match orderId");
        }
        String source = oneOf(root, "purchaseSource", Set.of("BUY_NOW", "CART"));
        String participant = oneOf(root, "stockParticipantType", Set.of("REGULAR_STOCK_HOLD"));
        return new OrderConfirmedV2(event.eventId(), "OrderConfirmed", 2, "order-service", "ORDER", orderId,
                event.aggregateVersion(), event.correlationId(), event.causationId(), event.occurredAt(),
                event.traceparent(), event.tracestate(), new OrderConfirmedDataV2(orderId,
                        boundedText(root, "orderNumber", 64), uuid(root, "purchaseRequestId"), source,
                        participant, uuid(root, "stockReferenceId"), uuid(root, "paymentId"),
                        instant(root, "confirmedAt")));
    }

    private JsonNode read(String payload) {
        try {
            JsonNode value = objectMapper.readTree(payload);
            if (value == null || !value.isObject()) throw invalid("payload must be an object");
            return value;
        } catch (IOException exception) {
            throw new IllegalArgumentException("OrderConfirmedV2 payload cannot be decoded", exception);
        }
    }

    private String oneOf(JsonNode root, String field, Set<String> allowed) {
        String value = text(root, field);
        if (!allowed.contains(value)) throw invalid("unsupported " + field);
        return value;
    }

    private UUID uuid(JsonNode root, String field) {
        try { return UUID.fromString(text(root, field)); }
        catch (IllegalArgumentException exception) { throw invalid(field + " is not a UUID"); }
    }

    private Instant instant(JsonNode root, String field) {
        try { return Instant.parse(text(root, field)); }
        catch (RuntimeException exception) { throw invalid(field + " is not an instant"); }
    }

    private String boundedText(JsonNode root, String field, int maximumLength) {
        String value = text(root, field);
        if (value.length() > maximumLength) throw invalid(field + " exceeds maximum length");
        return value;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " is missing");
        }
        return value.asText();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
