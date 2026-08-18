package com.philia.flashsale.payment.payment.application.model;

import java.util.UUID;

/** Authenticated owner command for creating or resuming the current hosted Checkout attempt. */
public record StartCheckoutCommand(UUID paymentId, UUID userId, String idempotencyKey) {
    public StartCheckoutCommand {
        if (paymentId == null || userId == null) {
            throw new IllegalArgumentException("paymentId and userId are required");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 1 || idempotencyKey.length() > 255
                || !idempotencyKey.chars().allMatch(value -> value >= 0x21 && value <= 0x7e)) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-255 printable characters");
        }
    }
}
