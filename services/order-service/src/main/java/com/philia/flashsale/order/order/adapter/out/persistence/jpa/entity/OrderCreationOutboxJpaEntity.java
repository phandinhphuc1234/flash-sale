package com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable OrderCreated publication snapshot and relay state. */
@Entity
@Table(name = "order_outbox_events")
public class OrderCreationOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;
    @Column(name = "causation_id", nullable = false)
    private UUID causationId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(length = 256)
    private String traceparent;
    @Column(length = 512)
    private String tracestate;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "claimed_by", length = 200)
    private String claimedBy;
    @Column(name = "claim_until")
    private Instant claimUntil;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderCreationOutboxJpaEntity() {
    }

    public static OrderCreationOutboxJpaEntity from(OrderCreationCandidate candidate) {
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = candidate.outboxEventId();
        entity.aggregateType = "ORDER";
        entity.aggregateId = candidate.order().id();
        entity.aggregateVersion = 1;
        entity.eventType = "OrderCreated";
        entity.eventVersion = 1;
        entity.eventKey = candidate.order().id().toString();
        entity.correlationId = candidate.correlationId();
        entity.causationId = candidate.causationId();
        entity.payload = snapshotPayload(candidate);
        entity.traceparent = candidate.traceparent();
        entity.tracestate = candidate.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = candidate.createdAt();
        entity.occurredAt = candidate.occurredAt();
        entity.createdAt = candidate.createdAt();
        entity.updatedAt = candidate.createdAt();
        return entity;
    }

    /** Creates the stable PaymentRequested command intent in the same local transaction. */
    public static OrderCreationOutboxJpaEntity paymentRequested(OrderCreationCandidate candidate) {
        if (candidate.purchaseSaga() == null || candidate.paymentRequestedOutboxEventId() == null) {
            throw new IllegalArgumentException("PaymentRequested outbox identity is missing");
        }
        var saga = candidate.purchaseSaga();
        var order = candidate.order();
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = candidate.paymentRequestedOutboxEventId();
        entity.aggregateType = "PURCHASE_SAGA";
        entity.aggregateId = order.id();
        entity.aggregateVersion = 1;
        entity.eventType = "PaymentRequested";
        entity.eventVersion = 1;
        entity.eventKey = order.id().toString();
        entity.correlationId = candidate.correlationId();
        entity.causationId = candidate.causationId();
        entity.payload = paymentRequestedPayload(order, saga);
        entity.traceparent = candidate.traceparent();
        entity.tracestate = candidate.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = candidate.createdAt();
        entity.occurredAt = candidate.occurredAt();
        entity.createdAt = candidate.createdAt();
        entity.updatedAt = candidate.createdAt();
        return entity;
    }

    /** Creates the stable Flash Sale confirm command after a verified PaymentSucceeded fact. */
    public static OrderCreationOutboxJpaEntity confirmReservation(PaymentSucceededCommand command,
            PurchaseSaga saga, UUID commandId) {
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = commandId;
        entity.aggregateType = "PURCHASE_SAGA";
        entity.aggregateId = saga.id();
        entity.aggregateVersion = saga.version();
        entity.eventType = "ConfirmPurchaseReservation";
        entity.eventVersion = 1;
        entity.eventKey = saga.orderId().toString();
        entity.correlationId = command.correlationId();
        entity.causationId = command.eventId();
        entity.payload = "{"
                + "\"sagaId\":\"" + saga.id() + "\","
                + "\"orderId\":\"" + saga.orderId() + "\","
                + "\"purchaseRequestId\":\"" + saga.purchaseRequestId() + "\","
                + "\"reservationId\":\"" + saga.reservationId() + "\","
                + "\"paymentId\":\"" + command.paymentId() + "\","
                + "\"paidAt\":\"" + command.paidAt() + "\"}";
        entity.traceparent = command.traceparent();
        entity.tracestate = command.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = command.occurredAt();
        entity.occurredAt = command.occurredAt();
        entity.createdAt = command.occurredAt();
        entity.updatedAt = command.occurredAt();
        return entity;
    }

    /** Creates the stable Flash Sale release command after a terminal PaymentFailed fact. */
    public static OrderCreationOutboxJpaEntity releaseReservation(PaymentFailedCommand command,
            PurchaseSaga saga, UUID commandId) {
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = commandId;
        entity.aggregateType = "PURCHASE_SAGA";
        entity.aggregateId = saga.id();
        entity.aggregateVersion = saga.version();
        entity.eventType = "ReleasePurchaseReservation";
        entity.eventVersion = 1;
        entity.eventKey = saga.orderId().toString();
        entity.correlationId = command.correlationId();
        entity.causationId = command.eventId();
        entity.payload = "{" +
                "\"sagaId\":\"" + saga.id() + "\"," +
                "\"orderId\":\"" + saga.orderId() + "\"," +
                "\"purchaseRequestId\":\"" + saga.purchaseRequestId() + "\"," +
                "\"reservationId\":\"" + saga.reservationId() + "\"," +
                "\"reason\":\"" + command.reason() + "\"}";
        entity.traceparent = command.traceparent();
        entity.tracestate = command.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = command.occurredAt();
        entity.occurredAt = command.occurredAt();
        entity.createdAt = command.occurredAt();
        entity.updatedAt = command.occurredAt();
        return entity;
    }

    /** Creates the terminal OrderConfirmed fact in the same local transaction as the state changes. */
    public static OrderCreationOutboxJpaEntity orderConfirmed(PurchaseReservationConfirmedCommand command,
            PurchaseSaga saga, OrderJpaEntity order) {
        UUID eventId = UUID.nameUUIDFromBytes(("order-confirmed:" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.confirmedAt();
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = eventId;
        entity.aggregateType = "ORDER";
        entity.aggregateId = order.getId();
        entity.aggregateVersion = saga.version();
        entity.eventType = "OrderConfirmed";
        entity.eventVersion = 1;
        entity.eventKey = order.getId().toString();
        entity.correlationId = command.correlationId();
        entity.causationId = command.eventId();
        entity.payload = "{"
                + "\"orderId\":\"" + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"paymentId\":\"" + command.paymentId() + "\","
                + "\"confirmedAt\":\"" + occurredAt + "\"}";
        entity.traceparent = command.traceparent();
        entity.tracestate = command.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = occurredAt;
        entity.occurredAt = occurredAt;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        return entity;
    }

    /** Creates the terminal OrderCancelled/OrderExpired fact after reservation release. */
    public static OrderCreationOutboxJpaEntity orderCancelled(PurchaseReservationReleasedCommand command,
            PurchaseSaga saga, OrderJpaEntity order) {
        return terminalRelease(command, saga, order, "OrderCancelled", "cancelledAt");
    }

    public static OrderCreationOutboxJpaEntity orderExpired(PurchaseReservationReleasedCommand command,
            PurchaseSaga saga, OrderJpaEntity order) {
        return terminalRelease(command, saga, order, "OrderExpired", "expiredAt");
    }

    /** Creates the stable correction fact for a verified late payment after release. */
    public static OrderCreationOutboxJpaEntity orderPaymentReviewRequired(
            PaymentSucceededCommand command, PurchaseSaga saga, OrderJpaEntity order,
            String previousStatus) {
        UUID eventId = UUID.nameUUIDFromBytes(("order-payment-review-required:" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.paidAt();
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = eventId;
        entity.aggregateType = "ORDER";
        entity.aggregateId = order.getId();
        entity.aggregateVersion = saga.version();
        entity.eventType = "OrderPaymentReviewRequired";
        entity.eventVersion = 1;
        entity.eventKey = order.getId().toString();
        entity.correlationId = command.correlationId();
        entity.causationId = command.eventId();
        entity.payload = "{"
                + "\"orderId\":\"" + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"paymentId\":\"" + command.paymentId() + "\","
                + "\"previousStatus\":\"" + previousStatus + "\","
                + "\"reviewReason\":\"LATE_PAYMENT_RESERVATION_UNAVAILABLE\","
                + "\"reviewRequiredAt\":\"" + occurredAt + "\"}";
        entity.traceparent = command.traceparent();
        entity.tracestate = command.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = occurredAt;
        entity.occurredAt = occurredAt;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        return entity;
    }

    /** Creates the same correction when the confirm attempt loses a release race. */
    public static OrderCreationOutboxJpaEntity orderPaymentReviewRequired(
            PurchaseReservationReleasedCommand command, PurchaseSaga saga, OrderJpaEntity order) {
        UUID eventId = UUID.nameUUIDFromBytes(("order-payment-review-required:" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.releasedAt();
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = eventId;
        entity.aggregateType = "ORDER";
        entity.aggregateId = order.getId();
        entity.aggregateVersion = saga.version();
        entity.eventType = "OrderPaymentReviewRequired";
        entity.eventVersion = 1;
        entity.eventKey = order.getId().toString();
        entity.correlationId = command.correlationId();
        entity.causationId = saga.activeCommandId() == null ? command.eventId() : saga.activeCommandId();
        entity.payload = "{"
                + "\"orderId\":\"" + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"paymentId\":\"" + saga.paymentId() + "\","
                + "\"previousStatus\":\"" + order.getStatus() + "\","
                + "\"reviewReason\":\"LATE_PAYMENT_RESERVATION_UNAVAILABLE\","
                + "\"reviewRequiredAt\":\"" + occurredAt + "\"}";
        entity.traceparent = command.traceparent();
        entity.tracestate = command.tracestate();
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = occurredAt;
        entity.occurredAt = occurredAt;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        return entity;
    }

    private static OrderCreationOutboxJpaEntity terminalRelease(PurchaseReservationReleasedCommand command,
            PurchaseSaga saga, OrderJpaEntity order, String eventType, String timeField) {
        UUID eventId = UUID.nameUUIDFromBytes((eventType + ":" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.releasedAt();
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = eventId; entity.aggregateType = "ORDER"; entity.aggregateId = order.getId();
        entity.aggregateVersion = saga.version(); entity.eventType = eventType; entity.eventVersion = 1;
        entity.eventKey = order.getId().toString(); entity.correlationId = command.correlationId();
        entity.causationId = command.eventId();
        entity.payload = "{"
                + "\"orderId\":\"" + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"reason\":\"" + command.reason() + "\","
                + "\"" + timeField + "\":\"" + occurredAt + "\"}";
        entity.traceparent = command.traceparent(); entity.tracestate = command.tracestate();
        entity.status = "PENDING"; entity.attemptCount = 0; entity.nextAttemptAt = occurredAt;
        entity.occurredAt = occurredAt; entity.createdAt = occurredAt; entity.updatedAt = occurredAt;
        return entity;
    }

    private static String paymentRequestedPayload(
            com.philia.flashsale.order.order.domain.model.Order order,
            com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga saga) {
        return "{"
                + "\"orderId\":\"" + order.id() + "\","
                + "\"userId\":\"" + order.userId() + "\","
                + "\"amount\":\"" + order.total().amount().toPlainString() + "\","
                + "\"currency\":\"" + order.currency() + "\","
                + "\"paymentDeadline\":\"" + saga.paymentDeadline() + "\"}";
    }

    private static String snapshotPayload(OrderCreationCandidate candidate) {
        var order = candidate.order();
        var line = order.line();
        return "{"
                + "\"orderId\":\"" + order.id() + "\","
                + "\"orderNumber\":\"" + order.orderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.purchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.reservationId() + "\","
                + "\"campaignId\":\"" + order.campaignId() + "\","
                + "\"userId\":\"" + order.userId() + "\","
                + "\"status\":\"" + order.status() + "\","
                + "\"currency\":\"" + order.currency() + "\","
                + "\"subtotalAmount\":\"" + order.subtotal().amount().toPlainString() + "\","
                + "\"totalAmount\":\"" + order.total().amount().toPlainString() + "\","
                + "\"acceptedAt\":\"" + order.acceptedAt() + "\","
                + "\"reservationExpiresAt\":\"" + order.reservationExpiresAt() + "\","
                + "\"items\":[{"
                + "\"variantId\":\"" + line.variantId() + "\","
                + "\"quantity\":" + line.quantity() + ","
                + "\"unitPrice\":\"" + line.unitPrice().amount().toPlainString() + "\","
                + "\"lineAmount\":\"" + line.lineAmount().amount().toPlainString() + "\"}]}";
    }

    public UUID getEventId() { return eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getEventKey() { return eventKey; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCausationId() { return causationId; }
    public String getPayload() { return payload; }
    public String getTraceparent() { return traceparent; }
    public String getTracestate() { return tracestate; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
}
