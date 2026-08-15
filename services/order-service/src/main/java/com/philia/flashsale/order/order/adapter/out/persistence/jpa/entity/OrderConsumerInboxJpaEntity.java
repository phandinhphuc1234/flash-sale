package com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable inbound event identity and canonical business fingerprint. */
@Entity
@Table(name = "order_consumer_inbox")
public class OrderConsumerInboxJpaEntity {

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
    @Column(name = "purchase_request_id", nullable = false)
    private UUID purchaseRequestId;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;
    @Column(name = "source_topic", nullable = false, length = 200)
    private String sourceTopic;
    @Column(name = "source_partition", nullable = false)
    private int sourcePartition;
    @Column(name = "source_offset", nullable = false)
    private long sourceOffset;
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected OrderConsumerInboxJpaEntity() {
    }

    public static OrderConsumerInboxJpaEntity from(OrderCreationCandidate candidate, OrderJpaEntity order) {
        OrderConsumerInboxJpaEntity entity = new OrderConsumerInboxJpaEntity();
        entity.eventId = candidate.eventId();
        entity.eventType = candidate.eventType();
        entity.eventVersion = candidate.eventVersion();
        entity.producer = candidate.producer();
        entity.aggregateId = candidate.order().purchaseRequestId();
        entity.aggregateVersion = candidate.aggregateVersion();
        entity.purchaseRequestId = candidate.order().purchaseRequestId();
        entity.reservationId = candidate.order().reservationId();
        entity.payloadFingerprint = candidate.fingerprint();
        entity.sourceTopic = candidate.sourceTopic();
        entity.sourcePartition = candidate.sourcePartition();
        entity.sourceOffset = candidate.sourceOffset();
        entity.order = order;
        entity.processedAt = candidate.occurredAt();
        return entity;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getProducer() { return producer; }
    public UUID getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public UUID getReservationId() { return reservationId; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public String getSourceTopic() { return sourceTopic; }
    public int getSourcePartition() { return sourcePartition; }
    public long getSourceOffset() { return sourceOffset; }
    public OrderJpaEntity getOrder() { return order; }
    public Instant getProcessedAt() { return processedAt; }
}
