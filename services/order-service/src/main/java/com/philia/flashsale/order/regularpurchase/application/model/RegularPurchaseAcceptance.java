package com.philia.flashsale.order.regularpurchase.application.model;

import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete durable regular-purchase commit assembled only after Product and Inventory decisions.
 *
 * <p>This application model deliberately contains no HTTP, Kafka, or JPA type. It is the exact
 * local transaction boundary for intake acceptance, the Order snapshot, the Saga, and both
 * publication intents.</p>
 */
public record RegularPurchaseAcceptance(
        RegularPurchaseRequest intake,
        Order order,
        PurchaseSaga saga,
        UUID orderCreatedEventId,
        UUID paymentRequestedEventId,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        String traceparent,
        String tracestate) {

    public RegularPurchaseAcceptance {
        Objects.requireNonNull(intake, "intake");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(saga, "saga");
        Objects.requireNonNull(orderCreatedEventId, "orderCreatedEventId");
        Objects.requireNonNull(paymentRequestedEventId, "paymentRequestedEventId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!intake.orderId().equals(order.id()) || !intake.id().equals(order.purchaseRequestId())) {
            throw new IllegalArgumentException("accepted intake and Order identities must agree");
        }
        if (!saga.orderId().equals(order.id()) || !saga.purchaseRequestId().equals(intake.id())) {
            throw new IllegalArgumentException("accepted Saga identities must agree with intake and Order");
        }
    }
}
