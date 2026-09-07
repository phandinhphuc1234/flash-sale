package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Adapter-local representation of the durable Order-owned purchase saga. */
@Entity
@Table(name = "purchase_sagas")
public class PurchaseSagaJpaEntity {
    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @Column(name = "purchase_request_id", nullable = false, unique = true)
    private UUID purchaseRequestId;
    @Column(name = "reservation_id", unique = true)
    private UUID reservationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_participant_type", nullable = false, length = 32)
    private StockParticipantType stockParticipantType;
    @Column(name = "stock_reference_id")
    private UUID stockReferenceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PurchaseSagaStatus status;
    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "last_payment_version")
    private Long lastPaymentVersion;
    @Column(name = "payment_succeeded_at")
    private Instant paymentSucceededAt;
    @Column(name = "payment_failure_reason")
    private String paymentFailureReason;
    @Column(name = "desired_order_status", length = 32)
    private String desiredOrderStatus;
    @Column(name = "manual_review_reason", length = 100)
    private String manualReviewReason;
    @Column(name = "active_command_id")
    private UUID activeCommandId;
    @Column(name = "step_started_at", nullable = false)
    private Instant stepStartedAt;
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseSagaJpaEntity() {
    }

    public static PurchaseSagaJpaEntity from(PurchaseSaga saga) {
        PurchaseSagaJpaEntity entity = new PurchaseSagaJpaEntity();
        entity.id = saga.id();
        entity.orderId = saga.orderId();
        entity.purchaseRequestId = saga.purchaseRequestId();
        entity.reservationId = saga.reservationId();
        entity.stockParticipantType = saga.stockParticipantType();
        entity.stockReferenceId = saga.stockReferenceId();
        entity.status = saga.status();
        entity.paymentDeadline = saga.paymentDeadline();
        entity.paymentId = saga.paymentId();
        entity.lastPaymentVersion = saga.lastPaymentVersion();
        entity.paymentSucceededAt = saga.paymentSucceededAt();
        entity.paymentFailureReason = saga.paymentFailureReason();
        entity.desiredOrderStatus = saga.desiredOrderStatus();
        entity.manualReviewReason = saga.manualReviewReason();
        entity.activeCommandId = saga.activeCommandId();
        entity.stepStartedAt = saga.stepStartedAt();
        entity.version = saga.version();
        entity.createdAt = saga.createdAt();
        entity.updatedAt = saga.updatedAt();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getPurchaseRequestId() { return purchaseRequestId; }
    public UUID getReservationId() { return reservationId; }
    public StockParticipantType getStockParticipantType() { return stockParticipantType; }
    public UUID getStockReferenceId() { return stockReferenceId; }
    public PurchaseSagaStatus getStatus() { return status; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public UUID getPaymentId() { return paymentId; }
    public Long getLastPaymentVersion() { return lastPaymentVersion; }
    public Instant getPaymentSucceededAt() { return paymentSucceededAt; }
    public String getPaymentFailureReason() { return paymentFailureReason; }
    public String getDesiredOrderStatus() { return desiredOrderStatus; }
    public String getManualReviewReason() { return manualReviewReason; }
    public UUID getActiveCommandId() { return activeCommandId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStepStartedAt() { return stepStartedAt; }

    /** Applies a framework-free Saga transition while keeping the mapping adapter-local. */
    public void apply(PurchaseSaga saga) {
        if (!id.equals(saga.id())) {
            throw new IllegalArgumentException("Saga identity mismatch");
        }
        status = saga.status();
        paymentId = saga.paymentId();
        lastPaymentVersion = saga.lastPaymentVersion();
        paymentSucceededAt = saga.paymentSucceededAt();
        paymentFailureReason = saga.paymentFailureReason();
        desiredOrderStatus = saga.desiredOrderStatus();
        manualReviewReason = saga.manualReviewReason();
        activeCommandId = saga.activeCommandId();
        stepStartedAt = saga.stepStartedAt();
        version = saga.version();
        updatedAt = saga.updatedAt();
    }
}
