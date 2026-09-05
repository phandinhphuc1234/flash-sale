package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotDataV1;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotItemV1;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/** Maps a durable CART confirmation outbox row to the versioned Cart command. */
public final class ReconcilePurchasedCartSnapshotAvroMapper {
    private final ObjectMapper objectMapper;

    public ReconcilePurchasedCartSnapshotAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ReconcilePurchasedCartSnapshotV1 map(OrderOutboxEvent event) {
        if (!"ReconcilePurchasedCartSnapshot".equals(event.eventType()) || event.eventVersion() != 1
                || !"ORDER".equals(event.aggregateType())) {
            throw new IllegalArgumentException("outbox envelope is not Cart reconciliation v1");
        }
        JsonNode root = read(event.payload());
        UUID orderId = uuid(root, "orderId");
        UUID purchaseRequestId = uuid(root, "purchaseRequestId");
        UUID cartId = uuid(root, "cartId");
        UUID ownerId = uuid(root, "ownerId");
        var items = new ArrayList<ReconcilePurchasedCartSnapshotItemV1>();
        for (JsonNode item : root.withArray("items")) {
            items.add(new ReconcilePurchasedCartSnapshotItemV1(uuid(item, "variantId"),
                    item.required("quantity").longValue(), item.required("itemVersion").longValue()));
        }
        if (items.isEmpty() || !orderId.equals(event.aggregateId()) || !purchaseRequestId.equals(event.correlationId())
                || !event.eventKey().equals(orderId.toString()) || event.causationId() == null) {
            throw new IllegalArgumentException("Cart reconciliation identity/key mismatch");
        }
        var data = new ReconcilePurchasedCartSnapshotDataV1(orderId, purchaseRequestId, cartId, ownerId,
                root.required("snapshotCartVersion").longValue(), instant(root, "confirmedAt"), items);
        return new ReconcilePurchasedCartSnapshotV1(event.eventId(), "ReconcilePurchasedCartSnapshot", 1,
                "order-service", "ORDER", event.aggregateId(), event.aggregateVersion(), event.correlationId(),
                event.causationId(), event.occurredAt(), event.traceparent(), event.tracestate(), data);
    }

    private JsonNode read(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("payload must be an object");
            return root;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cart reconciliation payload is invalid JSON", exception);
        }
    }

    private UUID uuid(JsonNode root, String field) {
        try {
            return UUID.fromString(root.required(field).asText());
        } catch (Exception exception) {
            throw new IllegalArgumentException(field + " is not a UUID", exception);
        }
    }

    private Instant instant(JsonNode root, String field) {
        try {
            return Instant.parse(root.required(field).asText());
        } catch (Exception exception) {
            throw new IllegalArgumentException(field + " is not an instant", exception);
        }
    }
}
