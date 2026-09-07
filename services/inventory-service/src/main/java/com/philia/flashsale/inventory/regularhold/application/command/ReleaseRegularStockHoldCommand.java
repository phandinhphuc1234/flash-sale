package com.philia.flashsale.inventory.regularhold.application.command;

import java.util.Objects;
import java.util.UUID;

/** Trusted Order command asking Inventory to release an unpaid regular hold. */
public record ReleaseRegularStockHoldCommand(
        UUID commandId,
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        UUID paymentId,
        String reason,
        String desiredOrderStatus
) {
    public ReleaseRegularStockHoldCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(paymentId, "paymentId");
        if (reason == null || reason.isBlank() || reason.length() > 80) {
            throw new IllegalArgumentException("Release reason must be present and bounded");
        }
        if (!"CANCELLED".equals(desiredOrderStatus) && !"EXPIRED".equals(desiredOrderStatus)) {
            throw new IllegalArgumentException("Release desiredOrderStatus must be CANCELLED or EXPIRED");
        }
    }
}
