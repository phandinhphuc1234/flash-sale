package com.philia.flashsale.order.regularpurchase.application.command;

import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CartCheckoutCommand(UUID shopperId, String idempotencyKey, long cartVersion,
        List<CartCheckoutLine> lines, String traceId, String traceparent, String tracestate) {
    public CartCheckoutCommand {
        Objects.requireNonNull(shopperId, "shopperId is required");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain one to 128 characters");
        }
        if (cartVersion < 0 || lines == null || lines.isEmpty() || lines.size() > 20) {
            throw new IllegalArgumentException("Cart checkout requires one to twenty lines");
        }
        lines = List.copyOf(lines);
    }
}
