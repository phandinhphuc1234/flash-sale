package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
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
        PurchaseSagaInboxJpaEntity entity = new PurchaseSagaInboxJpaEntity();
        entity.eventId = command.eventId();
        entity.eventType = command.eventType();
        entity.eventVersion = command.eventVersion();
        entity.producer = command.producer();
        entity.aggregateId = command.aggregateId();
        entity.aggregateVersion = command.aggregateVersion();
        entity.orderId = command.orderId();
        entity.payloadFingerprint = command.fingerprint();
        entity.sourceTopic = command.sourceTopic();
        entity.sourcePartition = command.sourcePartition();
        entity.sourceOffset = command.sourceOffset();
        entity.processedAt = command.occurredAt();
        return entity;
    }

    public UUID getEventId() { return eventId; }
    public UUID getOrderId() { return orderId; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
}
