package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable identity/fingerprint for participant facts consumed by the Order Saga. */
@Entity
@Table(name = "purchase_saga_inbox")
public class PurchaseSagaInboxJpaEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(nullable = false, length = 100)
    private String producer;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;
    @Column(name = "source_topic", nullable = false, length = 200)
    private String sourceTopic;
    @Column(name = "source_partition", nullable = false)
    private int sourcePartition;
    @Column(name = "source_offset", nullable = false)
    private long sourceOffset;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected PurchaseSagaInboxJpaEntity() {
    }

    public static PurchaseSagaInboxJpaEntity paymentSucceeded(PaymentSucceededCommand command) {
        return from(command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateId(), command.aggregateVersion(), command.orderId(), command.fingerprint(),
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), command.occurredAt());
    }

    public static PurchaseSagaInboxJpaEntity paymentFailed(PaymentFailedCommand command) {
        return from(command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateId(), command.aggregateVersion(), command.orderId(), command.fingerprint(),
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), command.failedAt());
    }

    public static PurchaseSagaInboxJpaEntity purchaseReservationConfirmed(
            PurchaseReservationConfirmedCommand command) {
        return from(command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateId(), command.aggregateVersion(), command.orderId(), command.fingerprint(),
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), command.confirmedAt());
    }

    public static PurchaseSagaInboxJpaEntity purchaseReservationReleased(
            PurchaseReservationReleasedCommand command) {
        return from(command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateId(), command.aggregateVersion(), command.orderId(), command.fingerprint(),
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), command.releasedAt());
    }

    public static PurchaseSagaInboxJpaEntity regularStockHoldConfirmed(
            RegularStockHoldConfirmedCommand command) {
        return from(command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateId(), command.aggregateVersion(), command.orderId(), command.fingerprint(),
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), command.transitionedAt());
    }

    public static PurchaseSagaInboxJpaEntity regularStockHoldOutcome(RegularStockHoldOutcomeCommand command) {
        return from(command.eventId(), command.eventType(), command.eventVersion(), command.producer(),
                command.aggregateId(), command.aggregateVersion(), command.orderId(), command.fingerprint(),
                command.sourceTopic(), command.sourcePartition(), command.sourceOffset(), command.transitionedAt());
    }

    private static PurchaseSagaInboxJpaEntity from(UUID eventId, String eventType, int eventVersion,
            String producer, UUID aggregateId, long aggregateVersion, UUID orderId, String payloadFingerprint,
            String sourceTopic, int sourcePartition, long sourceOffset, Instant processedAt) {
        PurchaseSagaInboxJpaEntity entity = new PurchaseSagaInboxJpaEntity();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.eventVersion = eventVersion;
        entity.producer = producer;
        entity.aggregateId = aggregateId;
        entity.aggregateVersion = aggregateVersion;
        entity.orderId = orderId;
        entity.payloadFingerprint = payloadFingerprint;
        entity.sourceTopic = sourceTopic;
        entity.sourcePartition = sourcePartition;
        entity.sourceOffset = sourceOffset;
        entity.processedAt = processedAt;
        return entity;
    }

    public UUID getEventId() { return eventId; }
    public UUID getOrderId() { return orderId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public Instant getProcessedAt() { return processedAt; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
}
