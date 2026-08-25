package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPurchaseReservationReleasePort;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationReleaseResult;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import jakarta.transaction.Transactional;
import java.util.Objects;

/** Commits Order terminalization, Saga compensation, inbox, and terminal outbox atomically. */
public class ReservationReleasePersistenceAdapter implements ApplyPurchaseReservationReleasePort {
    private final OrderJpaRepository orders; private final PurchaseSagaJpaRepository sagas;
    private final PurchaseSagaInboxJpaRepository inbox; private final OrderCreationOutboxJpaRepository outbox;
    public ReservationReleasePersistenceAdapter(OrderJpaRepository orders, PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        this.orders = Objects.requireNonNull(orders, "orders"); this.sagas = Objects.requireNonNull(sagas, "sagas"); this.inbox = Objects.requireNonNull(inbox, "inbox"); this.outbox = Objects.requireNonNull(outbox, "outbox");
    }
    @Override @Transactional
    public PurchaseReservationReleaseResult apply(PurchaseReservationReleasedCommand command) {
        PurchaseSagaJpaEntity storedSaga = sagas.findLockedByOrderId(command.orderId()).orElseThrow(() -> invalid("Order Saga does not exist"));
        OrderJpaEntity storedOrder = orders.findLockedById(command.orderId()).orElseThrow(() -> invalid("Order does not exist"));
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (existing.get().getPayloadFingerprint().equals(command.fingerprint())) return PurchaseReservationReleaseResult.replayed(command.orderId(), storedSaga.getId());
            return PurchaseReservationReleaseResult.conflict(command.orderId(), storedSaga.getId(), "PurchaseReservationReleased event identity was reused");
        }
        validateIdentity(command, storedSaga, storedOrder);
        OrderStatus desired = desiredStatus(storedSaga);
        if (command.reservationStatus() == null
                || !(command.reservationStatus().equals("RELEASED")
                || command.reservationStatus().equals("EXPIRED"))) {
            throw invalid("release result has an unsupported reservation status");
        }
        PurchaseSaga completed = toDomain(storedSaga).completeRelease(command.releasedAt());
        storedOrder.terminalize(desired, command.releasedAt());
        storedSaga.apply(completed);
        sagas.saveAndFlush(storedSaga); orders.saveAndFlush(storedOrder);
        inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.purchaseReservationReleased(command));
        outbox.saveAndFlush(desired == OrderStatus.EXPIRED
                ? OrderCreationOutboxJpaEntity.orderExpired(command, completed, storedOrder)
                : OrderCreationOutboxJpaEntity.orderCancelled(command, completed, storedOrder));
        return PurchaseReservationReleaseResult.applied(command.orderId(), storedSaga.getId());
    }
    private OrderStatus desiredStatus(PurchaseSagaJpaEntity saga) {
        if (saga.getStatus() != PurchaseSagaStatus.RELEASING_RESERVATION || saga.getDesiredOrderStatus() == null) throw invalid("Order Saga is not awaiting reservation release");
        try { return OrderStatus.valueOf(saga.getDesiredOrderStatus()); } catch (IllegalArgumentException exception) { throw invalid("Saga desired terminal status is invalid"); }
    }
    private void validateIdentity(PurchaseReservationReleasedCommand command, PurchaseSagaJpaEntity saga, OrderJpaEntity order) {
        if (!saga.getId().equals(command.sagaId()) || !saga.getPurchaseRequestId().equals(command.purchaseRequestId()) || !saga.getReservationId().equals(command.reservationId()) || !saga.getOrderId().equals(command.orderId()) || !order.getPurchaseRequestId().equals(command.purchaseRequestId()) || !order.getReservationId().equals(command.reservationId()) || !order.getId().equals(command.orderId())) throw invalid("reservation release identity conflicts with Order Saga");
    }
    private PurchaseSaga toDomain(PurchaseSagaJpaEntity entity) {
        return PurchaseSaga.restore(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(), entity.getReservationId(), entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(), entity.getLastPaymentVersion(), entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(), entity.getDesiredOrderStatus(), entity.getActiveCommandId(), entity.getStepStartedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private IllegalStateException invalid(String message) { return new IllegalStateException(message); }
}
