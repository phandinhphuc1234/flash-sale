package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
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
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.dao.DataAccessException;

/** PostgreSQL boundary for the atomic PaymentSucceeded -> confirm-command transition. */
public final class PaymentSuccessPersistenceAdapter implements ApplyPaymentSuccessPort {
    private final PurchaseSagaJpaRepository sagas;
    private final PurchaseSagaInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;

    public PaymentSuccessPersistenceAdapter(PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        this.sagas = Objects.requireNonNull(sagas, "sagas");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    @Override
    @Transactional
    public PaymentSuccessResult apply(PaymentSucceededCommand command) {
        Objects.requireNonNull(command, "command");
        PurchaseSagaJpaEntity stored = sagas.findLockedByOrderId(command.orderId())
                .orElseThrow(() -> new InvalidPaymentSuccessException("Order Saga does not exist"));
        var commandId = ApplyPaymentSuccessService.confirmCommandId(command.eventId());
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (!existing.get().getPayloadFingerprint().equals(command.fingerprint())) {
                throw new InvalidPaymentSuccessException("PaymentSucceeded event identity was reused");
            }
            return PaymentSuccessResult.replayed(stored.getOrderId(), stored.getId(), commandId);
        }
        if (stored.getPaymentId() != null && !stored.getPaymentId().equals(command.paymentId())) {
            throw new InvalidPaymentSuccessException("Payment identity conflicts with Saga");
        }
        if (stored.getLastPaymentVersion() != null
                && command.aggregateVersion() < stored.getLastPaymentVersion()) {
            throw new InvalidPaymentSuccessException("Payment version regressed");
        }
        try {
            PurchaseSaga current = toDomain(stored);
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

    private PurchaseSaga toDomain(PurchaseSagaJpaEntity entity) {
        if (entity.getPaymentId() != null) {
            throw new InvalidPaymentSuccessException("Payment Saga is already processing a payment");
        }
        return PurchaseSaga.start(entity.getOrderId(), entity.getPurchaseRequestId(), entity.getReservationId(),
                entity.getPaymentDeadline().plusSeconds(30), entity.getCreatedAt());
    }
}
