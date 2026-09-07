package com.philia.flashsale.order.order.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderLineJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderLineJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OwnedOrderQueryJpaRepository;
import com.philia.flashsale.order.order.application.port.out.ListOwnedOrdersPort;
import com.philia.flashsale.order.order.application.port.out.LoadOwnedOrderPort;
import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderItemResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.order.application.result.OrderSummaryResult;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter that keeps owner filtering inside the database query. */
public class OwnedOrderQueryJpaAdapter implements LoadOwnedOrderPort, ListOwnedOrdersPort {
    private static final Sort ORDER_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OwnedOrderQueryJpaRepository orders;
    private final OrderLineJpaRepository lines;

    public OwnedOrderQueryJpaAdapter(OwnedOrderQueryJpaRepository orders, OrderLineJpaRepository lines) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.lines = Objects.requireNonNull(lines, "lines");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDetailsResult> load(GetOwnedOrderQuery query) {
        Objects.requireNonNull(query, "query");
        return orders.findByIdAndUserId(query.orderId(), query.ownerId())
                .map(this::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPageResult load(ListOwnedOrdersQuery query) {
        Objects.requireNonNull(query, "query");
        var page = orders.findByUserId(query.ownerId(), PageRequest.of(query.page(), query.size(), ORDER_SORT));
        return new OrderPageResult(
                page.getContent().stream().map(this::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private OrderDetailsResult toDetails(OrderJpaEntity order) {
        return new OrderDetailsResult(
                order.getId(), order.getOrderNumber(), order.getPurchaseRequestId(),
                order.getReservationId(), order.getCampaignId(), order.getPurchaseSource(),
                order.getStockParticipantType(), order.getStockReferenceId(), order.getStatus(),
                order.getCurrency(), order.getSubtotalAmount(), order.getTotalAmount(),
                order.getAcceptedAt(), order.getReservationExpiresAt(),
                order.getPurchaseSource() == com.philia.flashsale.order.order.domain.model.PurchaseSource.FLASH_SALE
                        ? null : order.getReservationExpiresAt(),
                lines.findByOrder_IdOrderByIdAsc(order.getId()).stream().map(this::toItem).toList(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private OrderSummaryResult toSummary(OrderJpaEntity order) {
        return new OrderSummaryResult(
                order.getId(), order.getOrderNumber(), order.getStatus(), order.getPurchaseSource(), order.getCurrency(),
                order.getTotalAmount(), order.getReservationExpiresAt(), order.getCreatedAt());
    }

    private OrderItemResult toItem(OrderLineJpaEntity line) {
        return new OrderItemResult(line.getVariantId(), line.getQuantity(), line.getUnitPrice(),
                line.getLineAmount());
    }
}
