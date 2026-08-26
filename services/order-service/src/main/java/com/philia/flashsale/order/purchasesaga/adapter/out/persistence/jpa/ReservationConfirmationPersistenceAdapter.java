package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidPurchaseReservationConfirmationException;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPurchaseReservationConfirmationPort;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import jakarta.transaction.Transactional;
import java.util.Objects;

/** Commits Order CONFIRMED, Saga COMPLETED, inbox, and OrderConfirmed outbox atomically. */
public class ReservationConfirmationPersistenceAdapter
        implements ApplyPurchaseReservationConfirmationPort {
    private final OrderJpaRepository orders;
    private final PurchaseSagaJpaRepository sagas;
    private final PurchaseSagaInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;

    public ReservationConfirmationPersistenceAdapter(OrderJpaRepository orders,
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.sagas = Objects.requireNonNull(sagas, "sagas");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    @Override
    @Transactional
    public PurchaseReservationConfirmationResult apply(PurchaseReservationConfirmedCommand command) {
        Objects.requireNonNull(command, "command");
        PurchaseSagaJpaEntity storedSaga = sagas.findLockedByOrderId(command.orderId())
                .orElseThrow(() -> invalid("Order Saga does not exist"));
        OrderJpaEntity storedOrder = orders.findLockedById(command.orderId())
                .orElseThrow(() -> invalid("Order does not exist"));
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (existing.get().getPayloadFingerprint().equals(command.fingerprint())) {
                return PurchaseReservationConfirmationResult.replayed(command.orderId(), storedSaga.getId());
            }
            return PurchaseReservationConfirmationResult.conflict(command.orderId(), storedSaga.getId(),
                    "PurchaseReservationConfirmed event identity was reused");
        }
        validateIdentity(command, storedSaga, storedOrder);
        if (storedSaga.getStatus() != com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus.CONFIRMING_RESERVATION) {
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.purchaseReservationConfirmed(command));
            return PurchaseReservationConfirmationResult.stale(command.orderId(), storedSaga.getId());
        }
        PurchaseSaga current = toDomain(storedSaga);
        PurchaseSaga completed = current.completeReservation(command.reservationId(), command.paymentId(),
                command.confirmedAt());
        storedOrder.confirm(command.confirmedAt());
        storedSaga.apply(completed);
        sagas.saveAndFlush(storedSaga);
        orders.saveAndFlush(storedOrder);
        inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.purchaseReservationConfirmed(command));
        outbox.saveAndFlush(OrderCreationOutboxJpaEntity.orderConfirmed(command, completed, storedOrder));
        return PurchaseReservationConfirmationResult.applied(command.orderId(), storedSaga.getId());
    }

    private void validateIdentity(PurchaseReservationConfirmedCommand command,
            PurchaseSagaJpaEntity saga, OrderJpaEntity order) {
        if (!saga.getId().equals(command.sagaId())
                || !saga.getPurchaseRequestId().equals(command.purchaseRequestId())
                || !saga.getReservationId().equals(command.reservationId())
                || !saga.getOrderId().equals(command.orderId())
                || !order.getPurchaseRequestId().equals(command.purchaseRequestId())
                || !order.getReservationId().equals(command.reservationId())
                || !order.getId().equals(command.orderId())) {
            throw invalid("reservation confirmation identity conflicts with Order Saga");
        }
        if (saga.getPaymentId() != null && !saga.getPaymentId().equals(command.paymentId())) {
            throw invalid("payment identity conflicts with Order Saga");
        }
    }

    private PurchaseSaga toDomain(PurchaseSagaJpaEntity entity) {
        return PurchaseSaga.restore(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(),
                entity.getReservationId(), entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(),
                entity.getLastPaymentVersion(), entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(),
                entity.getDesiredOrderStatus(), entity.getManualReviewReason(), entity.getActiveCommandId(),
                entity.getStepStartedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private InvalidPurchaseReservationConfirmationException invalid(String message) {
        return new InvalidPurchaseReservationConfirmationException(message);
    }
}
