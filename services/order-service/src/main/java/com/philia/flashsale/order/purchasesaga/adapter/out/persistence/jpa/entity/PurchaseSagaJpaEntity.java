package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
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
    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PurchaseSagaStatus status;
    @Column(name = "payment_deadline", nullable = false)
    private Instant paymentDeadline;
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
        entity.status = saga.status();
        entity.paymentDeadline = saga.paymentDeadline();
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
    public PurchaseSagaStatus getStatus() { return status; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
