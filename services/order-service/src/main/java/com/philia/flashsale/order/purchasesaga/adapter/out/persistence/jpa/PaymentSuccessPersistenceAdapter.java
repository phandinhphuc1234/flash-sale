package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidPaymentSuccessException;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPaymentSuccessPort;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPaymentSuccessService;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.dao.DataAccessException;

/** PostgreSQL boundary for the atomic PaymentSucceeded -> confirm-command transition. */
public class PaymentSuccessPersistenceAdapter implements ApplyPaymentSuccessPort {
    private final PurchaseSagaJpaRepository sagas;
    private final OrderJpaRepository orders;
    private final PurchaseSagaInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;

    public PaymentSuccessPersistenceAdapter(PurchaseSagaJpaRepository sagas, OrderJpaRepository orders,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        this.sagas = Objects.requireNonNull(sagas, "sagas");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    @Override
    @Transactional
    public PaymentSuccessResult apply(PaymentSucceededCommand command) {
        Objects.requireNonNull(command, "command");
        PurchaseSagaJpaEntity stored = sagas.findLockedByOrderId(command.orderId())
                .orElseThrow(() -> new InvalidPaymentSuccessException("Order Saga does not exist"));
        OrderJpaEntity order = orders.findLockedById(command.orderId())
                .orElseThrow(() -> new InvalidPaymentSuccessException("Order does not exist"));
        var commandId = ApplyPaymentSuccessService.confirmCommandId(command.eventId());
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (!existing.get().getPayloadFingerprint().equals(command.fingerprint())) {
                throw new InvalidPaymentSuccessException("PaymentSucceeded event identity was reused");
            }
            return PaymentSuccessResult.replayed(stored.getOrderId(), stored.getId(), commandId);
        }
        if (stored.getPaymentId() != null && !stored.getPaymentId().equals(command.paymentId())) {
            return PaymentSuccessResult.conflict(command.orderId(), stored.getId(), "Payment identity conflicts with Saga");
        }
        validateAmountAndCurrency(command, order);
        if (stored.getLastPaymentVersion() != null
                && command.aggregateVersion() < stored.getLastPaymentVersion()) {
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.paymentSucceeded(command));
            return PaymentSuccessResult.stale(command.orderId(), stored.getId());
        }
        if (stored.getLastPaymentVersion() != null
                && command.aggregateVersion() == stored.getLastPaymentVersion()
                && stored.getStatus() != PurchaseSagaStatus.PAYMENT_PENDING) {
            return PaymentSuccessResult.conflict(command.orderId(), stored.getId(),
                    "Payment version identity conflicts with the active Saga fact");
        }
        try {
            PurchaseSaga current = toDomain(stored);
            if (current.status() == PurchaseSagaStatus.COMPENSATED) {
                String previousStatus = order.getStatus().name();
                PurchaseSaga reviewed = current.enterManualReview(command.paymentId(),
                        command.aggregateVersion(), command.paidAt(),
                        "LATE_PAYMENT_RESERVATION_UNAVAILABLE", command.occurredAt());
                order.reopenForManualReview(command.occurredAt());
                stored.apply(reviewed);
                sagas.saveAndFlush(stored);
                orders.saveAndFlush(order);
                inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.paymentSucceeded(command));
                outbox.saveAndFlush(OrderCreationOutboxJpaEntity.orderPaymentReviewRequired(
                        command, reviewed, order, previousStatus));
                return PaymentSuccessResult.manualReview(command.orderId(), stored.getId(),
                        reviewed.manualReviewReason());
            }
            if (current.status() == PurchaseSagaStatus.MANUAL_REVIEW
                    || current.status() == PurchaseSagaStatus.COMPLETED) {
                return PaymentSuccessResult.stale(command.orderId(), stored.getId());
            }
            PurchaseSaga transitioned = current.confirmReservation(command.paymentId(),
                    command.aggregateVersion(), command.paidAt(), commandId, command.occurredAt());
            stored.apply(transitioned);
            sagas.saveAndFlush(stored);
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.paymentSucceeded(command));
            outbox.saveAndFlush(OrderCreationOutboxJpaEntity.confirmReservation(command, transitioned, commandId));
            return PaymentSuccessResult.applied(stored.getOrderId(), stored.getId(), commandId);
        } catch (DataAccessException exception) {
            throw new InvalidPaymentSuccessException("PaymentSucceeded persistence failed: "
                    + exception.getClass().getSimpleName());
        }
    }

    private void validateAmountAndCurrency(PaymentSucceededCommand command, OrderJpaEntity order) {
        BigDecimal expected = order.getTotalAmount().setScale(4);
        if (expected.compareTo(command.amount().setScale(4)) != 0
                || !order.getCurrency().equals(command.currency())) {
            throw new InvalidPaymentSuccessException("PaymentSucceeded amount or currency conflicts with Order");
        }
    }

    private PurchaseSaga toDomain(PurchaseSagaJpaEntity entity) {
        return PurchaseSaga.restore(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(), entity.getReservationId(),
                entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(), entity.getLastPaymentVersion(),
                entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(), entity.getDesiredOrderStatus(),
                entity.getManualReviewReason(),
                entity.getActiveCommandId(), entity.getStepStartedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
