package com.philia.flashsale.order.order.application.result;

import java.util.List;

/** Application-owned bounded page result. */
public record OrderPageResult(
        List<OrderSummaryResult> orders,
        int page,
        int size,
        long totalElements) {
    public OrderPageResult {
        orders = orders == null ? List.of() : List.copyOf(orders);
        if (page < 0 || size <= 0 || totalElements < 0) {
            throw new IllegalArgumentException("invalid Order page metadata");
        }
    }
}
