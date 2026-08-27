package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderPaymentReviewRequiredDataV1;
import com.philia.flashsale.contract.order.event.v1.OrderPaymentReviewRequiredV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Maps the late-payment correction outbox snapshot to its versioned Avro fact. */
public final class OrderPaymentReviewRequiredAvroMapper {
    private final ObjectMapper objectMapper;

    public OrderPaymentReviewRequiredAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderPaymentReviewRequiredV1 map(OrderOutboxEvent event) {
        if (event == null || !"OrderPaymentReviewRequired".equals(event.eventType())
                || event.eventVersion() != 1 || !"ORDER".equals(event.aggregateType())) {
            throw invalid("outbox envelope is not OrderPaymentReviewRequired.v1");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(event.payload());
        } catch (IOException exception) {
            throw invalid("OrderPaymentReviewRequired payload is not valid JSON");
        }
        UUID orderId = uuid(root, "orderId");
        if (!orderId.equals(event.aggregateId()) || !event.eventKey().equals(orderId.toString())) {
            throw invalid("OrderPaymentReviewRequired identity/key mismatch");
        }
        String previousStatus = text(root, "previousStatus");
        if (!"CANCELLED".equals(previousStatus) && !"EXPIRED".equals(previousStatus)) {
            throw invalid("previousStatus is not a terminal unpaid status");
        }
        String reason = text(root, "reviewReason");
        if (!"LATE_PAYMENT_RESERVATION_UNAVAILABLE".equals(reason)) {
            throw invalid("reviewReason is not the approved correction reason");
        }
        var data = new OrderPaymentReviewRequiredDataV1(orderId, text(root, "orderNumber"),
                uuid(root, "purchaseRequestId"), uuid(root, "reservationId"), uuid(root, "paymentId"),
                previousStatus, reason, instant(root, "reviewRequiredAt"));
        return new OrderPaymentReviewRequiredV1(event.eventId(), event.eventType(), event.eventVersion(),
                "order-service", event.aggregateType(), event.aggregateId(), event.aggregateVersion(),
                event.correlationId(), event.causationId(), event.occurredAt(), data);
    }

    private UUID uuid(JsonNode root, String field) {
        try { return UUID.fromString(text(root, field)); }
        catch (RuntimeException exception) { throw invalid(field + " is not a UUID"); }
    }

    private Instant instant(JsonNode root, String field) {
        try { return Instant.parse(text(root, field)); }
        catch (RuntimeException exception) { throw invalid(field + " is not an instant"); }
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
