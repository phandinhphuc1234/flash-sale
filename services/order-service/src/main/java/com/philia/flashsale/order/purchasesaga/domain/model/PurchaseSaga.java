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
    private final UUID paymentId;
    private final Long lastPaymentVersion;
    private final Instant paymentSucceededAt;
    private final UUID activeCommandId;
    private final Instant stepStartedAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PurchaseSaga(UUID id, UUID orderId, UUID purchaseRequestId, UUID reservationId,
            PurchaseSagaStatus status, Instant paymentDeadline, UUID paymentId, Long lastPaymentVersion,
            Instant paymentSucceededAt, UUID activeCommandId, Instant stepStartedAt,
            long version, Instant createdAt, Instant updatedAt, boolean initial) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.purchaseRequestId = Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.status = Objects.requireNonNull(status, "status");
        this.paymentDeadline = Objects.requireNonNull(paymentDeadline, "paymentDeadline");
        this.paymentId = paymentId;
        this.lastPaymentVersion = lastPaymentVersion;
        this.paymentSucceededAt = paymentSucceededAt;
        this.activeCommandId = activeCommandId;
        this.stepStartedAt = Objects.requireNonNull(stepStartedAt, "stepStartedAt");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!id.equals(purchaseRequestId)) {
            throw new InvalidPurchaseSagaException("saga id must equal purchaseRequestId");
        }
        if (initial && (status != PurchaseSagaStatus.PAYMENT_PENDING || version != 0)) {
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
                PurchaseSagaStatus.PAYMENT_PENDING, deadline, null, null, null, null,
                createdAt, 0, createdAt, createdAt, true);
    }

    /** Applies one verified PaymentSucceeded fact and opens the confirm-reservation step. */
    public PurchaseSaga confirmReservation(UUID paymentId, long paymentVersion, Instant paidAt,
            UUID commandId, Instant transitionedAt) {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(transitionedAt, "transitionedAt");
        if (paymentVersion <= 0) {
            throw new InvalidPurchaseSagaException("payment version must be positive");
        }
        if (status != PurchaseSagaStatus.PAYMENT_PENDING
                && status != PurchaseSagaStatus.RELEASING_RESERVATION) {
            throw new InvalidPurchaseSagaException("Saga is not eligible for reservation confirmation");
        }
        if (lastPaymentVersion != null && paymentVersion < lastPaymentVersion) {
            throw new InvalidPurchaseSagaException("payment version regressed");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, reservationId,
                PurchaseSagaStatus.CONFIRMING_RESERVATION, paymentDeadline, paymentId,
                paymentVersion, paidAt, commandId, transitionedAt, version + 1, createdAt,
                transitionedAt, false);
    }

    public UUID id() { return id; }
    public UUID orderId() { return orderId; }
    public UUID purchaseRequestId() { return purchaseRequestId; }
    public UUID reservationId() { return reservationId; }
    public PurchaseSagaStatus status() { return status; }
    public Instant paymentDeadline() { return paymentDeadline; }
    public UUID paymentId() { return paymentId; }
    public Long lastPaymentVersion() { return lastPaymentVersion; }
    public Instant paymentSucceededAt() { return paymentSucceededAt; }
    public UUID activeCommandId() { return activeCommandId; }
    public Instant stepStartedAt() { return stepStartedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
