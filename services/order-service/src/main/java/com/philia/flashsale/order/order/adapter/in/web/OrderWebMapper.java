package com.philia.flashsale.order.order.adapter.in.web;

import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderDetailsResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderItemResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderSummaryResponse;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderItemResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.order.application.result.OrderSummaryResult;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps application read models to the stable public HTTP contract. */
@Component
public class OrderWebMapper {

    public OrderDetailsResponse toDetailsResponse(OrderDetailsResult result) {
        return new OrderDetailsResponse(result.id(), result.orderNumber(), result.purchaseRequestId(),
                result.reservationId(), result.campaignId(), result.purchaseSource(),
                result.stockParticipantType(), result.stockReferenceId(), result.status(), result.currency(),
                result.subtotalAmount(), result.totalAmount(), result.acceptedAt(),
                result.reservationExpiresAt(), result.stockHoldExpiresAt(),
                result.items().stream().map(this::toItem).toList(),
                result.createdAt(), result.updatedAt());
    }

    public PageResponse<OrderSummaryResponse> toPageResponse(OrderPageResult result) {
        List<OrderSummaryResponse> data = result.orders().stream().map(this::toSummary).toList();
        return PageResponse.of(data, result.page(), result.size(), result.totalElements());
    }

    private OrderItemResponse toItem(OrderItemResult result) {
        return new OrderItemResponse(result.variantId(), result.quantity(), result.unitPrice(),
                result.lineAmount());
    }

    private OrderSummaryResponse toSummary(OrderSummaryResult result) {
        return new OrderSummaryResponse(result.id(), result.orderNumber(), result.status(), result.purchaseSource(),
                result.currency(), result.totalAmount(), result.reservationExpiresAt(), result.createdAt());
    }
}
