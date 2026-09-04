package com.philia.flashsale.inventory.regularhold.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity ledger for one consumed Order command and its stable Inventory outcome event. */
public record RegularHoldCommandInboxEntry(
        UUID commandId,
        String commandType,
        UUID orderId,
        UUID holdId,
        UUID purchaseRequestId,
        long aggregateVersion,
        String payloadFingerprint,
        UUID resultEventId,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        Instant processedAt
) {
    public RegularHoldCommandInboxEntry {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(commandType, "commandType");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        if (aggregateVersion <= 0) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        if (payloadFingerprint == null || !payloadFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadFingerprint must be SHA-256 hex");
        }
        Objects.requireNonNull(resultEventId, "resultEventId");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        if (sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("source position must be non-negative");
        }
        Objects.requireNonNull(processedAt, "processedAt");
    }
}
