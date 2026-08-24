package com.philia.flashsale.order.purchasesaga.domain.model;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import com.philia.flashsale.order.purchasesaga.domain.policy.PurchaseSagaDeadlinePolicy;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Order-owned durable workflow identity created with an accepted reservation. */
public final class PurchaseSaga {
    private final UUID id;
    private final UUID orderId;
    private final UUID purchaseRequestId;
    private final UUID reservationId;
    private final PurchaseSagaStatus status;
    private final Instant paymentDeadline;
    private final Instant stepStartedAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PurchaseSaga(UUID id, UUID orderId, UUID purchaseRequestId, UUID reservationId,
            PurchaseSagaStatus status, Instant paymentDeadline, Instant stepStartedAt,
            long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.purchaseRequestId = Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.status = Objects.requireNonNull(status, "status");
        this.paymentDeadline = Objects.requireNonNull(paymentDeadline, "paymentDeadline");
        this.stepStartedAt = Objects.requireNonNull(stepStartedAt, "stepStartedAt");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!id.equals(purchaseRequestId)) {
            throw new InvalidPurchaseSagaException("saga id must equal purchaseRequestId");
        }
        if (status != PurchaseSagaStatus.PAYMENT_PENDING || version != 0) {
            throw new InvalidPurchaseSagaException("new purchase saga must start at PAYMENT_PENDING version 0");
        }
        if (!paymentDeadline.isAfter(createdAt)) {
            throw new InvalidPurchaseSagaException("payment deadline must be after saga creation");
        }
    }

    public static PurchaseSaga start(UUID orderId, UUID purchaseRequestId, UUID reservationId,
            Instant reservationExpiresAt, Instant createdAt) {
        Instant deadline = PurchaseSagaDeadlinePolicy.paymentDeadline(reservationExpiresAt, createdAt);
        return new PurchaseSaga(purchaseRequestId, orderId, purchaseRequestId, reservationId,
                PurchaseSagaStatus.PAYMENT_PENDING, deadline, createdAt, 0, createdAt, createdAt);
    }

    public UUID id() { return id; }
    public UUID orderId() { return orderId; }
    public UUID purchaseRequestId() { return purchaseRequestId; }
    public UUID reservationId() { return reservationId; }
    public PurchaseSagaStatus status() { return status; }
    public Instant paymentDeadline() { return paymentDeadline; }
    public Instant stepStartedAt() { return stepStartedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
