package com.philia.flashsale.order.order.application.usecase;

import com.philia.flashsale.order.order.application.exception.InvalidOrderQueryException;
import com.philia.flashsale.order.order.application.exception.OrderNotFoundException;
import com.philia.flashsale.order.order.application.port.in.GetOwnedOrderUseCase;
import com.philia.flashsale.order.order.application.port.in.ListOwnedOrdersUseCase;
import com.philia.flashsale.order.order.application.port.out.ListOwnedOrdersPort;
import com.philia.flashsale.order.order.application.port.out.LoadOwnedOrderPort;
import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.observability.OrderObservability;
import java.util.Objects;

/** Orchestrates owner queries without exposing persistence or HTTP concerns inward. */
public class OrderQueryService implements GetOwnedOrderUseCase, ListOwnedOrdersUseCase {
    private final LoadOwnedOrderPort detailPort;
    private final ListOwnedOrdersPort listPort;
    private final int maxPageSize;
    private final OrderObservability observability;

    public OrderQueryService(LoadOwnedOrderPort detailPort, ListOwnedOrdersPort listPort, int maxPageSize) {
        this(detailPort, listPort, maxPageSize, OrderObservability.noop());
    }

    public OrderQueryService(LoadOwnedOrderPort detailPort, ListOwnedOrdersPort listPort, int maxPageSize,
            OrderObservability observability) {
        this.detailPort = Objects.requireNonNull(detailPort, "detailPort");
        this.listPort = Objects.requireNonNull(listPort, "listPort");
        if (maxPageSize <= 0) {
            throw new IllegalArgumentException("maxPageSize must be positive");
        }
        this.maxPageSize = maxPageSize;
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public OrderDetailsResult get(GetOwnedOrderQuery query) {
        Objects.requireNonNull(query, "query");
        return observability.observe(OrderObservability.Operation.OWNER_DETAIL_QUERY, "listed",
                () -> detailPort.load(query).orElseThrow(() -> new OrderNotFoundException(query.orderId())));
    }

    @Override
    public OrderPageResult list(ListOwnedOrdersQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.size() > maxPageSize) {
            throw new InvalidOrderQueryException("size must not exceed " + maxPageSize, "size");
        }
        return observability.observe(OrderObservability.Operation.OWNER_LIST_QUERY, "listed",
                () -> listPort.load(query));
    }
}
