package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderLineJpaRepository;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidRegularHoldOutcomeException;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyRegularHoldOutcomePort;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Commits regular-hold outcome, Order/Saga state, inbox, and terminal outbox atomically. */
public class RegularHoldRecoveryPersistenceAdapter implements ApplyRegularHoldOutcomePort {
    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final PurchaseSagaJpaRepository sagas;
    private final PurchaseSagaInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;

    public RegularHoldRecoveryPersistenceAdapter(OrderJpaRepository orders, OrderLineJpaRepository lines,
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.sagas = Objects.requireNonNull(sagas, "sagas");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    @Override
    @Transactional
    public RegularHoldOutcomeResult apply(RegularStockHoldOutcomeCommand command) {
        Objects.requireNonNull(command, "command");
        PurchaseSagaJpaEntity storedSaga = sagas.findLockedByOrderId(command.orderId())
                .orElseThrow(() -> invalid("Order Saga does not exist"));
        OrderJpaEntity storedOrder = orders.findLockedById(command.orderId())
                .orElseThrow(() -> invalid("Order does not exist"));
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (existing.get().getPayloadFingerprint().equals(command.fingerprint())) {
                return RegularHoldOutcomeResult.replayed(command.orderId(), storedSaga.getId());
            }
            return RegularHoldOutcomeResult.conflict(command.orderId(), storedSaga.getId(),
                    "regular hold outcome event identity was reused");
        }
        validateIdentityAndSnapshot(command, storedSaga, storedOrder);
        var latest = inbox.findFirstByOrderIdAndAggregateIdAndProducerOrderByAggregateVersionDesc(
                command.orderId(), command.holdId(), "inventory-service");
        if (latest.isPresent() && command.aggregateVersion() < latest.get().getAggregateVersion()) {
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
            return RegularHoldOutcomeResult.stale(command.orderId(), storedSaga.getId());
        }
        if (latest.isPresent() && command.aggregateVersion() == latest.get().getAggregateVersion()) {
            if (storedSaga.getStatus() != PurchaseSagaStatus.COMPENSATED) {
                throw invalid("regular hold outcome version conflicts with its current Saga state");
            }
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
            return RegularHoldOutcomeResult.replayed(command.orderId(), storedSaga.getId());
        }

        PurchaseSaga current = restore(storedSaga);
        validateCausation(command, current);
        if (current.status() == PurchaseSagaStatus.CONFIRMING_STOCK) {
            PurchaseSaga reviewed = current.enterManualReviewAfterReservationFailure(command.transitionedAt());
            storedSaga.apply(reviewed);
            sagas.saveAndFlush(storedSaga);
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
            outbox.saveAndFlush(OrderCreationOutboxJpaEntity.regularOrderPaymentReviewRequired(
                    command, reviewed, storedOrder));
            return RegularHoldOutcomeResult.manualReview(command.orderId(), storedSaga.getId());
        }

        if ("EXPIRED".equals(command.status())) {
            if (current.status() != PurchaseSagaStatus.PAYMENT_PENDING
                    && current.status() != PurchaseSagaStatus.RELEASING_STOCK) {
                inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
                return RegularHoldOutcomeResult.stale(command.orderId(), storedSaga.getId());
            }
            PurchaseSaga expired = current.completeRegularStockExpiry(command.transitionedAt());
            storedOrder.terminalize(OrderStatus.EXPIRED, command.transitionedAt());
            storedSaga.apply(expired);
            sagas.saveAndFlush(storedSaga);
            orders.saveAndFlush(storedOrder);
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
            outbox.saveAndFlush(OrderCreationOutboxJpaEntity.regularOrderExpired(command, expired, storedOrder));
            return RegularHoldOutcomeResult.applied(command.orderId(), storedSaga.getId());
        }

        if (current.status() != PurchaseSagaStatus.RELEASING_STOCK) {
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
            return RegularHoldOutcomeResult.stale(command.orderId(), storedSaga.getId());
        }
        PurchaseSaga compensated = current.completeRegularStockRelease(command.transitionedAt());
        OrderStatus desired = desiredStatus(compensated);
        storedOrder.terminalize(desired, command.transitionedAt());
        storedSaga.apply(compensated);
        sagas.saveAndFlush(storedSaga);
        orders.saveAndFlush(storedOrder);
        inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldOutcome(command));
        outbox.saveAndFlush(desired == OrderStatus.EXPIRED
                ? OrderCreationOutboxJpaEntity.regularOrderExpired(command, compensated, storedOrder)
                : OrderCreationOutboxJpaEntity.regularOrderCancelled(command, compensated, storedOrder));
        return RegularHoldOutcomeResult.applied(command.orderId(), storedSaga.getId());
    }

    private void validateCausation(RegularStockHoldOutcomeCommand command, PurchaseSaga saga) {
        if ("EXPIRED".equals(command.status())) {
            if (!saga.purchaseRequestId().equals(command.causationId())) {
                throw invalid("expired regular hold outcome must be caused by hold creation");
            }
            return;
        }
        if (saga.status() == PurchaseSagaStatus.RELEASING_STOCK
                && !Objects.equals(saga.activeCommandId(), command.causationId())) {
            throw invalid("released regular hold outcome does not match the active release command");
        }
    }

    private void validateIdentityAndSnapshot(RegularStockHoldOutcomeCommand command,
            PurchaseSagaJpaEntity saga, OrderJpaEntity order) {
        if (saga.getStockParticipantType() != StockParticipantType.REGULAR_STOCK_HOLD
                || order.getStockParticipantType() != StockParticipantType.REGULAR_STOCK_HOLD
                || !saga.getId().equals(command.sagaId()) || !saga.getPurchaseRequestId().equals(command.purchaseRequestId())
                || !saga.getOrderId().equals(command.orderId()) || !saga.getStockReferenceId().equals(command.holdId())
                || !order.getId().equals(command.orderId()) || !order.getPurchaseRequestId().equals(command.purchaseRequestId())
                || !order.getStockReferenceId().equals(command.holdId())) {
            throw invalid("regular hold outcome identity conflicts with Order Saga");
        }
        if ("RELEASED".equals(command.status()) && saga.getPaymentId() == null) {
            // The released fact is caused by ReleaseRegularStockHold, not PaymentFailed itself.
            // The payment identity is already carried by the persisted Saga.
            throw invalid("released regular hold has no payment identity");
        }
        Map<UUID, Long> expected = lines.findByOrder_IdOrderByIdAsc(order.getId()).stream()
                .collect(Collectors.toMap(line -> line.getVariantId(), line -> line.getQuantity()));
        Map<UUID, Long> actual = command.items().stream()
                .collect(Collectors.toMap(item -> item.variantId(), item -> item.quantity()));
        if (expected.isEmpty() || !expected.equals(actual)) {
            throw invalid("regular hold outcome items conflict with immutable Order lines");
        }
    }

    private OrderStatus desiredStatus(PurchaseSaga saga) {
        try { return OrderStatus.valueOf(saga.desiredOrderStatus()); }
        catch (RuntimeException exception) { throw invalid("Saga desired terminal status is invalid"); }
    }

    private PurchaseSaga restore(PurchaseSagaJpaEntity entity) {
        return PurchaseSaga.restoreRegular(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(),
                entity.getStockReferenceId(), entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(),
                entity.getLastPaymentVersion(), entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(),
                entity.getDesiredOrderStatus(), entity.getManualReviewReason(), entity.getActiveCommandId(),
                entity.getStepStartedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private InvalidRegularHoldOutcomeException invalid(String message) {
        return new InvalidRegularHoldOutcomeException(message);
    }
}
