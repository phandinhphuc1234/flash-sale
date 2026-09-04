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

    private static final String ORDER_ID_JSON_FIELD = "\"orderId\":\"";
    private static final String PURCHASE_SAGA_AGGREGATE_TYPE = "PURCHASE_SAGA";

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
        return pendingEvent(candidate.outboxEventId(), "ORDER", candidate.order().id(), 1,
                "OrderCreated", candidate.order().id().toString(), candidate.correlationId(),
                candidate.causationId(), snapshotPayload(candidate), candidate.traceparent(), candidate.tracestate(),
                candidate.createdAt(), candidate.occurredAt(), candidate.createdAt());
    }

    /** Creates the stable PaymentRequested command intent in the same local transaction. */
    public static OrderCreationOutboxJpaEntity paymentRequested(OrderCreationCandidate candidate) {
        if (candidate.purchaseSaga() == null || candidate.paymentRequestedOutboxEventId() == null) {
            throw new IllegalArgumentException("PaymentRequested outbox identity is missing");
        }
        var saga = candidate.purchaseSaga();
        var order = candidate.order();
        return pendingEvent(candidate.paymentRequestedOutboxEventId(), PURCHASE_SAGA_AGGREGATE_TYPE, order.id(), 1,
                "PaymentRequested", order.id().toString(), candidate.correlationId(), candidate.causationId(),
                paymentRequestedPayload(order, saga), candidate.traceparent(), candidate.tracestate(),
                candidate.createdAt(), candidate.occurredAt(), candidate.createdAt());
    }

    /** Creates the additive V2 regular-Order fact without changing the Flash Sale V1 snapshot. */
    public static OrderCreationOutboxJpaEntity regularOrderCreated(UUID eventId,
            com.philia.flashsale.order.order.domain.model.Order order, PurchaseSaga saga,
            UUID correlationId, UUID causationId, String traceparent, String tracestate, Instant occurredAt) {
        return pendingEvent(eventId, "ORDER", order.id(), 1, "OrderCreatedV2", 2,
                order.id().toString(), correlationId, causationId,
                regularOrderCreatedPayload(order, saga), traceparent, tracestate,
                occurredAt, occurredAt, occurredAt);
    }

    /** Reuses the established PaymentRequestedV1 contract for an accepted regular Order. */
    public static OrderCreationOutboxJpaEntity paymentRequested(UUID eventId,
            com.philia.flashsale.order.order.domain.model.Order order, PurchaseSaga saga,
            UUID correlationId, UUID causationId, String traceparent, String tracestate, Instant occurredAt) {
        return pendingEvent(eventId, PURCHASE_SAGA_AGGREGATE_TYPE, order.id(), 1,
                "PaymentRequested", 1, order.id().toString(), correlationId, causationId,
                paymentRequestedPayload(order, saga), traceparent, tracestate,
                occurredAt, occurredAt, occurredAt);
    }

    /** Creates the stable Flash Sale confirm command after a verified PaymentSucceeded fact. */
    public static OrderCreationOutboxJpaEntity confirmReservation(PaymentSucceededCommand command,
            PurchaseSaga saga, UUID commandId) {
        String payload = "{"
                + "\"sagaId\":\"" + saga.id() + "\","
                + ORDER_ID_JSON_FIELD + saga.orderId() + "\","
                + "\"purchaseRequestId\":\"" + saga.purchaseRequestId() + "\","
                + "\"reservationId\":\"" + saga.reservationId() + "\","
                + "\"paymentId\":\"" + command.paymentId() + "\","
                + "\"paidAt\":\"" + command.paidAt() + "\"}";
        return pendingEvent(commandId, PURCHASE_SAGA_AGGREGATE_TYPE, saga.id(), saga.version(),
                "ConfirmPurchaseReservation", saga.orderId().toString(), command.correlationId(),
                command.eventId(), payload, command.traceparent(), command.tracestate(), command.occurredAt(),
                command.occurredAt(), command.occurredAt());
    }

    /** Creates the stable Flash Sale release command after a terminal PaymentFailed fact. */
    public static OrderCreationOutboxJpaEntity releaseReservation(PaymentFailedCommand command,
            PurchaseSaga saga, UUID commandId) {
        String payload = "{" +
                "\"sagaId\":\"" + saga.id() + "\"," +
                ORDER_ID_JSON_FIELD + saga.orderId() + "\"," +
                "\"purchaseRequestId\":\"" + saga.purchaseRequestId() + "\"," +
                "\"reservationId\":\"" + saga.reservationId() + "\"," +
                "\"reason\":\"" + command.reason() + "\"}";
        return pendingEvent(commandId, PURCHASE_SAGA_AGGREGATE_TYPE, saga.id(), saga.version(),
                "ReleasePurchaseReservation", saga.orderId().toString(), command.correlationId(),
                command.eventId(), payload, command.traceparent(), command.tracestate(), command.occurredAt(),
                command.occurredAt(), command.occurredAt());
    }

    /** Creates the terminal OrderConfirmed fact in the same local transaction as the state changes. */
    public static OrderCreationOutboxJpaEntity orderConfirmed(PurchaseReservationConfirmedCommand command,
            PurchaseSaga saga, OrderJpaEntity order) {
        UUID eventId = UUID.nameUUIDFromBytes(("order-confirmed:" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.confirmedAt();
        String payload = "{"
                + ORDER_ID_JSON_FIELD + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"paymentId\":\"" + command.paymentId() + "\","
                + "\"confirmedAt\":\"" + occurredAt + "\"}";
        return pendingEvent(eventId, "ORDER", order.getId(), saga.version(), "OrderConfirmed",
                order.getId().toString(), command.correlationId(), command.eventId(), payload,
                command.traceparent(), command.tracestate(), occurredAt, occurredAt, occurredAt);
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
        String payload = "{"
                + ORDER_ID_JSON_FIELD + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"paymentId\":\"" + command.paymentId() + "\","
                + "\"previousStatus\":\"" + previousStatus + "\","
                + "\"reviewReason\":\"LATE_PAYMENT_RESERVATION_UNAVAILABLE\","
                + "\"reviewRequiredAt\":\"" + occurredAt + "\"}";
        return pendingEvent(eventId, "ORDER", order.getId(), saga.version(), "OrderPaymentReviewRequired",
                order.getId().toString(), command.correlationId(), command.eventId(), payload,
                command.traceparent(), command.tracestate(), occurredAt, occurredAt, occurredAt);
    }

    /** Creates the same correction when the confirm attempt loses a release race. */
    public static OrderCreationOutboxJpaEntity orderPaymentReviewRequired(
            PurchaseReservationReleasedCommand command, PurchaseSaga saga, OrderJpaEntity order) {
        UUID eventId = UUID.nameUUIDFromBytes(("order-payment-review-required:" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.releasedAt();
        String payload = "{"
                + ORDER_ID_JSON_FIELD + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"paymentId\":\"" + saga.paymentId() + "\","
                + "\"previousStatus\":\"" + order.getStatus() + "\","
                + "\"reviewReason\":\"LATE_PAYMENT_RESERVATION_UNAVAILABLE\","
                + "\"reviewRequiredAt\":\"" + occurredAt + "\"}";
        UUID causationId = saga.activeCommandId() == null ? command.eventId() : saga.activeCommandId();
        return pendingEvent(eventId, "ORDER", order.getId(), saga.version(), "OrderPaymentReviewRequired",
                order.getId().toString(), command.correlationId(), causationId, payload,
                command.traceparent(), command.tracestate(), occurredAt, occurredAt, occurredAt);
    }

    private static OrderCreationOutboxJpaEntity terminalRelease(PurchaseReservationReleasedCommand command,
            PurchaseSaga saga, OrderJpaEntity order, String eventType, String timeField) {
        UUID eventId = UUID.nameUUIDFromBytes((eventType + ":" + command.eventId())
                .getBytes(StandardCharsets.UTF_8));
        Instant occurredAt = command.releasedAt();
        String payload = "{"
                + ORDER_ID_JSON_FIELD + order.getId() + "\","
                + "\"orderNumber\":\"" + order.getOrderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.getPurchaseRequestId() + "\","
                + "\"reservationId\":\"" + order.getReservationId() + "\","
                + "\"reason\":\"" + command.reason() + "\","
                + "\"" + timeField + "\":\"" + occurredAt + "\"}";
        return pendingEvent(eventId, "ORDER", order.getId(), saga.version(), eventType,
                order.getId().toString(), command.correlationId(), command.eventId(), payload,
                command.traceparent(), command.tracestate(), occurredAt, occurredAt, occurredAt);
    }

    private static OrderCreationOutboxJpaEntity pendingEvent(UUID eventId, String aggregateType,
            UUID aggregateId, long aggregateVersion, String eventType, String eventKey,
            UUID correlationId, UUID causationId, String payload, String traceparent, String tracestate,
            Instant nextAttemptAt, Instant occurredAt, Instant createdAt) {
        return pendingEvent(eventId, aggregateType, aggregateId, aggregateVersion, eventType, 1, eventKey,
                correlationId, causationId, payload, traceparent, tracestate, nextAttemptAt, occurredAt, createdAt);
    }

    private static OrderCreationOutboxJpaEntity pendingEvent(UUID eventId, String aggregateType,
            UUID aggregateId, long aggregateVersion, String eventType, int eventVersion, String eventKey,
            UUID correlationId, UUID causationId, String payload, String traceparent, String tracestate,
            Instant nextAttemptAt, Instant occurredAt, Instant createdAt) {
        OrderCreationOutboxJpaEntity entity = new OrderCreationOutboxJpaEntity();
        entity.eventId = eventId;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.aggregateVersion = aggregateVersion;
        entity.eventType = eventType;
        entity.eventVersion = eventVersion;
        entity.eventKey = eventKey;
        entity.correlationId = correlationId;
        entity.causationId = causationId;
        entity.payload = payload;
        entity.traceparent = traceparent;
        entity.tracestate = tracestate;
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = nextAttemptAt;
        entity.occurredAt = occurredAt;
        entity.createdAt = createdAt;
        entity.updatedAt = createdAt;
        return entity;
    }

    private static String paymentRequestedPayload(
            com.philia.flashsale.order.order.domain.model.Order order,
            com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga saga) {
        return "{"
                + ORDER_ID_JSON_FIELD + order.id() + "\","
                + "\"userId\":\"" + order.userId() + "\","
                + "\"amount\":\"" + order.total().amount().toPlainString() + "\","
                + "\"currency\":\"" + order.currency() + "\","
                + "\"paymentDeadline\":\"" + saga.paymentDeadline() + "\"}";
    }

    private static String regularOrderCreatedPayload(
            com.philia.flashsale.order.order.domain.model.Order order,
            com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga saga) {
        StringBuilder items = new StringBuilder();
        for (var line : order.lines()) {
            if (!items.isEmpty()) items.append(',');
            items.append("{\"variantId\":\"").append(line.variantId()).append("\",")
                    .append("\"quantity\":").append(line.quantity()).append(',')
                    .append("\"unitPrice\":\"").append(line.unitPrice().amount().toPlainString()).append("\",")
                    .append("\"lineAmount\":\"").append(line.lineAmount().amount().toPlainString()).append("\"}");
        }
        return "{"
                + ORDER_ID_JSON_FIELD + order.id() + "\","
                + "\"orderNumber\":\"" + order.orderNumber() + "\","
                + "\"purchaseRequestId\":\"" + order.purchaseRequestId() + "\","
                + "\"userId\":\"" + order.userId() + "\","
                + "\"purchaseSource\":\"" + order.purchaseSource() + "\","
                + "\"stockParticipantType\":\"" + order.stockParticipantType() + "\","
                + "\"stockReferenceId\":\"" + order.stockReferenceId() + "\","
                + "\"cartId\":" + nullableUuid(order.cartId()) + ','
                + "\"cartVersion\":" + nullableLong(order.cartVersion()) + ','
                + "\"status\":\"" + order.status() + "\","
                + "\"currency\":\"" + order.currency() + "\","
                + "\"subtotalAmount\":\"" + order.subtotal().amount().toPlainString() + "\","
                + "\"totalAmount\":\"" + order.total().amount().toPlainString() + "\","
                + "\"acceptedAt\":\"" + order.acceptedAt() + "\","
                + "\"stockHoldExpiresAt\":\"" + order.stockParticipantExpiresAt() + "\","
                + "\"paymentDeadline\":\"" + saga.paymentDeadline() + "\","
                + "\"items\":[" + items + "]}";
    }

    private static String nullableUuid(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String nullableLong(Long value) {
        return value == null ? "null" : value.toString();
    }

    private static String snapshotPayload(OrderCreationCandidate candidate) {
        var order = candidate.order();
        var line = order.line();
        return "{"
                + ORDER_ID_JSON_FIELD + order.id() + "\","
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
