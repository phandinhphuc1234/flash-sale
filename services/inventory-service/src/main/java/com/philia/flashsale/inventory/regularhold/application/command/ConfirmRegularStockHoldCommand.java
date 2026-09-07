package com.philia.flashsale.inventory.regularhold.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Trusted Order command input after strict Kafka envelope validation. */
public record ConfirmRegularStockHoldCommand(
        UUID commandId,
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        UUID paymentId,
        Instant paidAt
) {
    public ConfirmRegularStockHoldCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(paidAt, "paidAt");
    }
}
