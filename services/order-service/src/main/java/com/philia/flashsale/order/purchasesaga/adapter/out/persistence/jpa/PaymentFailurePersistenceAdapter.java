package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidPaymentFailureException;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPaymentFailurePort;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPaymentFailureService;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Objects;

/** PostgreSQL boundary for the atomic PaymentFailed -> release-command transition. */
public class PaymentFailurePersistenceAdapter implements ApplyPaymentFailurePort {
    private final OrderJpaRepository orders;
    private final PurchaseSagaJpaRepository sagas;
    private final PurchaseSagaInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;

    public PaymentFailurePersistenceAdapter(OrderJpaRepository orders, PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.sagas = Objects.requireNonNull(sagas, "sagas");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    @Override
    @Transactional
    public PaymentFailureResult apply(PaymentFailedCommand command) {
        Objects.requireNonNull(command, "command");
        PurchaseSagaJpaEntity stored = sagas.findLockedByOrderId(command.orderId())
                .orElseThrow(() -> invalid("Order Saga does not exist"));
        OrderJpaEntity order = orders.findLockedById(command.orderId())
                .orElseThrow(() -> invalid("Order does not exist"));
        var commandId = ApplyPaymentFailureService.releaseCommandId(command.eventId());
        var desiredStatus = ApplyPaymentFailureService.desiredOrderStatus(command.reason());
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (!existing.get().getPayloadFingerprint().equals(command.fingerprint())) {
                return PaymentFailureResult.conflict(command.orderId(), stored.getId(), "PaymentFailed event identity was reused");
            }
            return PaymentFailureResult.replayed(command.orderId(), stored.getId(), commandId, desiredStatus);
        }
        validate(command, order, stored);
        PurchaseSaga current = toDomain(stored);
        PurchaseSaga transitioned = current.stockParticipantType() == StockParticipantType.REGULAR_STOCK_HOLD
                ? current.releaseRegularStock(command.paymentId(), command.aggregateVersion(), command.reason(),
                        desiredStatus, command.failedAt(), commandId, command.occurredAt())
                : current.releaseReservation(command.paymentId(), command.aggregateVersion(), command.reason(),
                        desiredStatus, command.failedAt(), commandId, command.occurredAt());
        stored.apply(transitioned);
        sagas.saveAndFlush(stored);
        inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.paymentFailed(command));
        outbox.saveAndFlush(current.stockParticipantType() == StockParticipantType.REGULAR_STOCK_HOLD
                ? com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity
                        .releaseRegularStockHold(command, transitioned, commandId)
                : com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity
                        .releaseReservation(command, transitioned, commandId));
        return PaymentFailureResult.applied(command.orderId(), stored.getId(), commandId, desiredStatus);
    }

    private void validate(PaymentFailedCommand command, OrderJpaEntity order, PurchaseSagaJpaEntity saga) {
        if (!order.getId().equals(command.orderId()) || !saga.getOrderId().equals(command.orderId())) throw invalid("Order identity conflicts with PaymentFailed");
        BigDecimal expected = order.getTotalAmount().setScale(4);
        if (expected.compareTo(command.amount()) != 0 || !order.getCurrency().equals(command.currency())) throw invalid("PaymentFailed amount or currency conflicts with Order");
        if (saga.getLastPaymentVersion() != null && command.aggregateVersion() < saga.getLastPaymentVersion()) throw invalid("Payment version regressed");
    }

    private PurchaseSaga toDomain(PurchaseSagaJpaEntity entity) {
        if (entity.getStockParticipantType() == StockParticipantType.REGULAR_STOCK_HOLD) {
            return PurchaseSaga.restoreRegular(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(),
                    entity.getStockReferenceId(), entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(),
                    entity.getLastPaymentVersion(), entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(),
                    entity.getDesiredOrderStatus(), entity.getManualReviewReason(), entity.getActiveCommandId(),
                    entity.getStepStartedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
        }
        return PurchaseSaga.restore(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(), entity.getReservationId(),
                entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(), entity.getLastPaymentVersion(),
                entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(), entity.getDesiredOrderStatus(),
                entity.getManualReviewReason(), entity.getActiveCommandId(), entity.getStepStartedAt(), entity.getVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private InvalidPaymentFailureException invalid(String message) { return new InvalidPaymentFailureException(message); }
}
