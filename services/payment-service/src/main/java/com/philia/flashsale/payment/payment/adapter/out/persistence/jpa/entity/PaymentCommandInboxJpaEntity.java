package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable identity and canonical fingerprint for PaymentRequested.v1 commands. */
@Entity
@Table(name = "payment_command_inbox")
public class PaymentCommandInboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentJpaEntity payment;
    @Column(name = "processing_status", nullable = false, length = 24)
    private String processingStatus;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected PaymentCommandInboxJpaEntity() {
    }

    public static PaymentCommandInboxJpaEntity received(UUID eventId, String eventType, int eventVersion,
            UUID orderId, String payloadFingerprint, Instant receivedAt) {
        PaymentCommandInboxJpaEntity entity = new PaymentCommandInboxJpaEntity();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.eventVersion = eventVersion;
        entity.orderId = orderId;
        entity.payloadFingerprint = payloadFingerprint;
        entity.processingStatus = "RECEIVED";
        entity.receivedAt = receivedAt;
        return entity;
    }

    public void markProcessed(PaymentJpaEntity payment, Instant processedAt) {
        this.payment = payment;
        this.processingStatus = "PROCESSED";
        this.processedAt = processedAt;
    }

    public void markConflicted(Instant observedAt) {
        this.processingStatus = "CONFLICTED";
        this.processedAt = observedAt;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public UUID getOrderId() { return orderId; }
    public String getPayloadFingerprint() { return payloadFingerprint; }
    public PaymentJpaEntity getPayment() { return payment; }
    public String getProcessingStatus() { return processingStatus; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
