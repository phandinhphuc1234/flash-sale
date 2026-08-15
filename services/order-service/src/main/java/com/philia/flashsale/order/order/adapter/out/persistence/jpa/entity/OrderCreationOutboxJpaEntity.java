package com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
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
