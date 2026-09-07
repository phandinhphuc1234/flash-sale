package com.philia.flashsale.cart.application.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable Order-owned command used to remove only the Cart intent that was purchased. */
public record ReconcilePurchasedCartSnapshotCommand(
        UUID commandId,
        UUID orderId,
        UUID purchaseRequestId,
        UUID cartId,
        UUID ownerId,
        long snapshotCartVersion,
        Instant confirmedAt,
        List<Item> items,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate) {

    public ReconcilePurchasedCartSnapshotCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(cartId, "cartId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        if (snapshotCartVersion < 0 || items == null || items.isEmpty() || items.size() > 20
                || sourceTopic == null || sourceTopic.isBlank() || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("invalid Cart reconciliation command");
        }
        items = List.copyOf(items);
    }

    public String fingerprint() {
        StringBuilder canonical = new StringBuilder()
                .append(orderId).append('|').append(purchaseRequestId).append('|')
                .append(cartId).append('|').append(ownerId).append('|').append(snapshotCartVersion)
                .append('|').append(confirmedAt);
        items.stream().sorted(java.util.Comparator.comparing(Item::variantId)).forEach(item -> canonical
                .append('|').append(item.variantId()).append('|').append(item.quantity()).append('|')
                .append(item.itemVersion()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    public record Item(UUID variantId, long quantity, long itemVersion) {
        public Item {
            Objects.requireNonNull(variantId, "variantId");
            if (quantity < 1 || quantity > 10 || itemVersion <= 0) {
                throw new IllegalArgumentException("invalid Cart reconciliation item");
            }
        }
    }
}
