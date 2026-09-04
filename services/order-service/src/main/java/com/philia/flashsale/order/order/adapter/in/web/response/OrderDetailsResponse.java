package com.philia.flashsale.order.order.adapter.in.web.response;

import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
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
        PurchaseSource purchaseSource,
        StockParticipantType stockParticipantType,
        UUID stockReferenceId,
        OrderStatus status,
        String currency,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        Instant acceptedAt,
        Instant reservationExpiresAt,
        Instant stockHoldExpiresAt,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
    public OrderDetailsResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
