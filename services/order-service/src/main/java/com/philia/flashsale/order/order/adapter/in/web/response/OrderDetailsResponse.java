package com.philia.flashsale.order.order.adapter.in.web.response;

import com.philia.flashsale.order.order.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public owner-scoped order detail response. */
public record OrderDetailsResponse(
        UUID id,
        String orderNumber,
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        OrderStatus status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        Instant acceptedAt,
        Instant reservationExpiresAt,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
    public OrderDetailsResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
