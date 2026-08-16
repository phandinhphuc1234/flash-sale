package com.philia.flashsale.order.order.adapter.in.web.response;

import com.philia.flashsale.order.order.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public list item; owner, persistence and messaging identities never leave the service. */
public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant reservationExpiresAt,
        Instant createdAt) {
}
