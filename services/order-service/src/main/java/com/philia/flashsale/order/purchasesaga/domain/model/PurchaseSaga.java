package com.philia.flashsale.order.purchasesaga.domain.model;

import com.philia.flashsale.order.purchasesaga.domain.exception.InvalidPurchaseSagaException;
import com.philia.flashsale.order.purchasesaga.domain.policy.PurchaseSagaDeadlinePolicy;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Order-owned durable workflow identity created with an accepted reservation. */
public final class PurchaseSaga {
    private static final String PAYMENT_ID_FIELD = "paymentId";
    private static final String TRANSITIONED_AT_FIELD = "transitionedAt";

    private final UUID id;
    private final UUID orderId;
    private final UUID purchaseRequestId;
    private final StockParticipantType stockParticipantType;
    private final UUID stockReferenceId;
    private final UUID reservationId;
    private final PurchaseSagaStatus status;
    private final Instant paymentDeadline;
    private final UUID paymentId;
    private final Long lastPaymentVersion;
    private final Instant paymentSucceededAt;
    private final String paymentFailureReason;
    private final String desiredOrderStatus;
    private final String manualReviewReason;
    private final UUID activeCommandId;
    private final Instant stepStartedAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PurchaseSaga(UUID id, UUID orderId, UUID purchaseRequestId,
            StockParticipantType stockParticipantType, UUID stockReferenceId, UUID reservationId,
            PurchaseSagaStatus status, Instant paymentDeadline, UUID paymentId, Long lastPaymentVersion,
            Instant paymentSucceededAt, String paymentFailureReason, String desiredOrderStatus,
            String manualReviewReason, UUID activeCommandId, Instant stepStartedAt,
            long version, Instant createdAt, Instant updatedAt, boolean initial) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.purchaseRequestId = Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        this.stockParticipantType = Objects.requireNonNull(stockParticipantType, "stockParticipantType");
        this.stockReferenceId = Objects.requireNonNull(stockReferenceId, "stockReferenceId");
        this.reservationId = reservationId;
        this.status = Objects.requireNonNull(status, "status");
        this.paymentDeadline = Objects.requireNonNull(paymentDeadline, "paymentDeadline");
        this.paymentId = paymentId;
        this.lastPaymentVersion = lastPaymentVersion;
        this.paymentSucceededAt = paymentSucceededAt;
        this.paymentFailureReason = paymentFailureReason;
        this.desiredOrderStatus = desiredOrderStatus;
        this.manualReviewReason = manualReviewReason;
        this.activeCommandId = activeCommandId;
        this.stepStartedAt = Objects.requireNonNull(stepStartedAt, "stepStartedAt");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!id.equals(purchaseRequestId)) {
            throw new InvalidPurchaseSagaException("saga id must equal purchaseRequestId");
        }
        validateStockParticipant();
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
        return new PurchaseSaga(purchaseRequestId, orderId, purchaseRequestId,
                StockParticipantType.FLASH_SALE_RESERVATION, reservationId, reservationId,
                PurchaseSagaStatus.PAYMENT_PENDING, deadline, null, null, null, null, null, null, null,
                createdAt, 0, createdAt, createdAt, true);
    }

    /** Starts a regular-purchase Saga after the Order owns an accepted Inventory hold. */
    public static PurchaseSaga startRegular(UUID orderId, UUID purchaseRequestId, UUID regularHoldId,
            Instant holdExpiresAt, Instant createdAt) {
        Instant deadline = PurchaseSagaDeadlinePolicy.paymentDeadline(holdExpiresAt, createdAt);
        return new PurchaseSaga(purchaseRequestId, orderId, purchaseRequestId,
                StockParticipantType.REGULAR_STOCK_HOLD, regularHoldId, null,
                PurchaseSagaStatus.PAYMENT_PENDING, deadline, null, null, null, null, null, null, null,
                createdAt, 0, createdAt, createdAt, true);
    }

    /** Rehydrates a persisted Saga without allowing the application core to depend on JPA. */
    public static PurchaseSaga restore(UUID id, UUID orderId, UUID purchaseRequestId, UUID reservationId,
            PurchaseSagaStatus status, Instant paymentDeadline, UUID paymentId, Long lastPaymentVersion,
            Instant paymentSucceededAt, String paymentFailureReason, String desiredOrderStatus,
            String manualReviewReason, UUID activeCommandId, Instant stepStartedAt, long version,
            Instant createdAt, Instant updatedAt) {
        return new PurchaseSaga(id, orderId, purchaseRequestId,
                StockParticipantType.FLASH_SALE_RESERVATION, reservationId, reservationId, status, paymentDeadline,
                paymentId, lastPaymentVersion, paymentSucceededAt, paymentFailureReason, desiredOrderStatus,
                manualReviewReason, activeCommandId, stepStartedAt,
                version, createdAt, updatedAt, false);
    }

    /** Rehydrates a regular-purchase Saga without exposing persistence concerns to the domain. */
    public static PurchaseSaga restoreRegular(UUID id, UUID orderId, UUID purchaseRequestId, UUID regularHoldId,
            PurchaseSagaStatus status, Instant paymentDeadline, UUID paymentId, Long lastPaymentVersion,
            Instant paymentSucceededAt, String paymentFailureReason, String desiredOrderStatus,
            String manualReviewReason, UUID activeCommandId, Instant stepStartedAt, long version,
            Instant createdAt, Instant updatedAt) {
        return new PurchaseSaga(id, orderId, purchaseRequestId,
                StockParticipantType.REGULAR_STOCK_HOLD, regularHoldId, null, status, paymentDeadline,
                paymentId, lastPaymentVersion, paymentSucceededAt, paymentFailureReason, desiredOrderStatus,
                manualReviewReason, activeCommandId, stepStartedAt,
                version, createdAt, updatedAt, false);
    }

    /** Applies one verified PaymentSucceeded fact and opens the confirm-reservation step. */
    public PurchaseSaga confirmReservation(UUID paymentId, long paymentVersion, Instant paidAt,
            UUID commandId, Instant transitionedAt) {
        Objects.requireNonNull(paymentId, PAYMENT_ID_FIELD);
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(transitionedAt, TRANSITIONED_AT_FIELD);
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
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.CONFIRMING_RESERVATION, paymentDeadline, paymentId,
                paymentVersion, paidAt, null, null, null, commandId, transitionedAt, version + 1, createdAt,
                transitionedAt, false);
    }

    /** Applies one verified PaymentSucceeded fact and opens the regular-hold confirmation step. */
    public PurchaseSaga confirmRegularStock(UUID paymentId, long paymentVersion, Instant paidAt,
            UUID commandId, Instant transitionedAt) {
        Objects.requireNonNull(paymentId, PAYMENT_ID_FIELD);
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(transitionedAt, TRANSITIONED_AT_FIELD);
        if (stockParticipantType != StockParticipantType.REGULAR_STOCK_HOLD) {
            throw new InvalidPurchaseSagaException("Saga does not own a regular stock hold");
        }
        if (paymentVersion <= 0) {
            throw new InvalidPurchaseSagaException("payment version must be positive");
        }
        if (status != PurchaseSagaStatus.PAYMENT_PENDING) {
            throw new InvalidPurchaseSagaException("Saga is not eligible for regular stock confirmation");
        }
        if (lastPaymentVersion != null && paymentVersion < lastPaymentVersion) {
            throw new InvalidPurchaseSagaException("payment version regressed");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.CONFIRMING_STOCK, paymentDeadline, paymentId,
                paymentVersion, paidAt, null, null, null, commandId, transitionedAt, version + 1, createdAt,
                transitionedAt, false);
    }

    /** Applies one verified terminal PaymentFailed fact and opens reservation release. */
    public PurchaseSaga releaseReservation(UUID paymentId, long paymentVersion, String failureReason,
            String desiredStatus, Instant failedAt, UUID commandId, Instant transitionedAt) {
        Objects.requireNonNull(paymentId, PAYMENT_ID_FIELD);
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(desiredStatus, "desiredStatus");
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(transitionedAt, TRANSITIONED_AT_FIELD);
        if (paymentVersion <= 0) throw new InvalidPurchaseSagaException("payment version must be positive");
        if (!"CANCELLED".equals(desiredStatus) && !"EXPIRED".equals(desiredStatus)) {
            throw new InvalidPurchaseSagaException("invalid release target");
        }
        if (status != PurchaseSagaStatus.PAYMENT_PENDING) {
            throw new InvalidPurchaseSagaException("Saga is not eligible for reservation release");
        }
        if (lastPaymentVersion != null && paymentVersion < lastPaymentVersion) {
            throw new InvalidPurchaseSagaException("payment version regressed");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.RELEASING_RESERVATION, paymentDeadline, paymentId,
                paymentVersion, paymentSucceededAt, failureReason, desiredStatus,
                null, commandId, transitionedAt, version + 1, createdAt, transitionedAt, false);
    }

    /** Records verified late payment when the reservation cannot be safely reacquired. */
    public PurchaseSaga enterManualReview(UUID paymentId, long paymentVersion, Instant paidAt,
            String reason, Instant transitionedAt) {
        Objects.requireNonNull(paymentId, PAYMENT_ID_FIELD);
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(transitionedAt, TRANSITIONED_AT_FIELD);
        if (paymentVersion <= 0) throw new InvalidPurchaseSagaException("payment version must be positive");
        if (status != PurchaseSagaStatus.COMPENSATED) {
            throw new InvalidPurchaseSagaException("Saga is not eligible for manual review correction");
        }
        if (lastPaymentVersion != null && paymentVersion <= lastPaymentVersion) {
            throw new InvalidPurchaseSagaException("manual review payment version is not newer");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.MANUAL_REVIEW, paymentDeadline, paymentId, paymentVersion,
                paidAt, null, null, reason, null, transitionedAt, version + 1, createdAt,
                transitionedAt, false);
    }

    /** Moves an in-flight success to manual review when Flash Sale reports the reservation gone. */
    public PurchaseSaga enterManualReviewAfterReservationFailure(Instant transitionedAt) {
        Objects.requireNonNull(transitionedAt, TRANSITIONED_AT_FIELD);
        if (status != PurchaseSagaStatus.CONFIRMING_RESERVATION
                && status != PurchaseSagaStatus.RELEASING_RESERVATION) {
            throw new InvalidPurchaseSagaException("Saga is not awaiting reservation recovery");
        }
        if (paymentId == null || lastPaymentVersion == null) {
            throw new InvalidPurchaseSagaException("manual review requires a verified payment");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.MANUAL_REVIEW, paymentDeadline, paymentId, lastPaymentVersion,
                paymentSucceededAt, null, null, "LATE_PAYMENT_RESERVATION_UNAVAILABLE", null,
                transitionedAt, version + 1, createdAt, transitionedAt, false);
    }

    /** Applies the durable Flash Sale release result and closes the Saga. */
    public PurchaseSaga completeRelease(Instant releasedAt) {
        Objects.requireNonNull(releasedAt, "releasedAt");
        if (status != PurchaseSagaStatus.RELEASING_RESERVATION) {
            throw new InvalidPurchaseSagaException("Saga is not awaiting reservation release");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.COMPENSATED, paymentDeadline, paymentId, lastPaymentVersion,
                paymentSucceededAt, paymentFailureReason, desiredOrderStatus, manualReviewReason, null, releasedAt,
                version + 1, createdAt, releasedAt, false);
    }

    /** Applies the verified Flash Sale confirmation and completes the Order-owned Saga. */
    public PurchaseSaga completeReservation(UUID confirmedReservationId, UUID confirmedPaymentId,
            Instant confirmedAt) {
        Objects.requireNonNull(confirmedReservationId, "confirmedReservationId");
        Objects.requireNonNull(confirmedPaymentId, "confirmedPaymentId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        if (status != PurchaseSagaStatus.CONFIRMING_RESERVATION) {
            throw new InvalidPurchaseSagaException("Saga is not awaiting reservation confirmation");
        }
        if (!reservationId.equals(confirmedReservationId)) {
            throw new InvalidPurchaseSagaException("reservation identity conflicts with Saga");
        }
        if (paymentId == null || !paymentId.equals(confirmedPaymentId)) {
            throw new InvalidPurchaseSagaException("payment identity conflicts with Saga");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.COMPLETED, paymentDeadline, paymentId, lastPaymentVersion,
                paymentSucceededAt, null, null, null, null, confirmedAt, version + 1, createdAt, confirmedAt, false);
    }

    /** Applies the verified regular-hold confirmation and completes the Order-owned Saga. */
    public PurchaseSaga completeRegularStock(UUID confirmedHoldId, UUID confirmedPaymentId, Instant confirmedAt) {
        Objects.requireNonNull(confirmedHoldId, "confirmedHoldId");
        Objects.requireNonNull(confirmedPaymentId, "confirmedPaymentId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        if (stockParticipantType != StockParticipantType.REGULAR_STOCK_HOLD
                || status != PurchaseSagaStatus.CONFIRMING_STOCK) {
            throw new InvalidPurchaseSagaException("Saga is not awaiting regular stock confirmation");
        }
        if (!stockReferenceId.equals(confirmedHoldId)) {
            throw new InvalidPurchaseSagaException("regular hold identity conflicts with Saga");
        }
        if (paymentId == null || !paymentId.equals(confirmedPaymentId)) {
            throw new InvalidPurchaseSagaException("payment identity conflicts with Saga");
        }
        return new PurchaseSaga(id, orderId, purchaseRequestId, stockParticipantType, stockReferenceId, reservationId,
                PurchaseSagaStatus.COMPLETED, paymentDeadline, paymentId, lastPaymentVersion,
                paymentSucceededAt, null, null, null, null, confirmedAt, version + 1, createdAt, confirmedAt, false);
    }

    private void validateStockParticipant() {
        if (stockParticipantType == StockParticipantType.FLASH_SALE_RESERVATION) {
            if (reservationId == null || !reservationId.equals(stockReferenceId)) {
                throw new InvalidPurchaseSagaException("Flash Sale Saga participant identity is invalid");
            }
            if (status == PurchaseSagaStatus.CONFIRMING_STOCK || status == PurchaseSagaStatus.RELEASING_STOCK) {
                throw new InvalidPurchaseSagaException("Flash Sale Saga cannot use regular stock states");
            }
            return;
        }
        if (reservationId != null) {
            throw new InvalidPurchaseSagaException("regular stock Saga must not retain a reservation id");
        }
        if (status == PurchaseSagaStatus.CONFIRMING_RESERVATION
                || status == PurchaseSagaStatus.RELEASING_RESERVATION) {
            throw new InvalidPurchaseSagaException("regular stock Saga cannot use reservation states");
        }
    }

    public UUID id() { return id; }
    public UUID orderId() { return orderId; }
    public UUID purchaseRequestId() { return purchaseRequestId; }
    public StockParticipantType stockParticipantType() { return stockParticipantType; }
    public UUID stockReferenceId() { return stockReferenceId; }
    public UUID reservationId() { return reservationId; }
    public PurchaseSagaStatus status() { return status; }
    public Instant paymentDeadline() { return paymentDeadline; }
    public UUID paymentId() { return paymentId; }
    public Long lastPaymentVersion() { return lastPaymentVersion; }
    public Instant paymentSucceededAt() { return paymentSucceededAt; }
    public String paymentFailureReason() { return paymentFailureReason; }
    public String desiredOrderStatus() { return desiredOrderStatus; }
    public String manualReviewReason() { return manualReviewReason; }
    public UUID activeCommandId() { return activeCommandId; }
    public Instant stepStartedAt() { return stepStartedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
