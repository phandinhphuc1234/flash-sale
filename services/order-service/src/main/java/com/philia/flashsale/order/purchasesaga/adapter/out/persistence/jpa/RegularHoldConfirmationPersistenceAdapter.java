package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderLineJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.entity.RegularPurchaseRequestJpaEntity;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.repository.RegularPurchaseRequestJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidRegularHoldConfirmationException;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyRegularHoldConfirmationPort;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldConfirmationResult;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSagaStatus;
import com.philia.flashsale.order.purchasesaga.domain.model.StockParticipantType;
import jakarta.transaction.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transaction-owning boundary for an Inventory-confirmed regular hold.
 *
 * <p>The Saga, Order, inbox receipt, and additive OrderConfirmedV2 outbox fact are committed in
 * one local transaction, before the Kafka record is acknowledged.</p>
 */
public class RegularHoldConfirmationPersistenceAdapter implements ApplyRegularHoldConfirmationPort {
    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final PurchaseSagaJpaRepository sagas;
    private final PurchaseSagaInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;
    private final RegularPurchaseRequestJpaRepository regularRequests;
    private final ObjectMapper objectMapper;
    private final boolean cartReconciliationEnabled;

    public RegularHoldConfirmationPersistenceAdapter(OrderJpaRepository orders, OrderLineJpaRepository lines,
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox) {
        this(orders, lines, sagas, inbox, outbox, null, null, false);
    }

    public RegularHoldConfirmationPersistenceAdapter(OrderJpaRepository orders, OrderLineJpaRepository lines,
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox, RegularPurchaseRequestJpaRepository regularRequests) {
        this(orders, lines, sagas, inbox, outbox, regularRequests, null, false);
    }

    public RegularHoldConfirmationPersistenceAdapter(OrderJpaRepository orders, OrderLineJpaRepository lines,
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox, RegularPurchaseRequestJpaRepository regularRequests,
            ObjectMapper objectMapper, boolean cartReconciliationEnabled) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.lines = Objects.requireNonNull(lines, "lines");
        this.sagas = Objects.requireNonNull(sagas, "sagas");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.regularRequests = regularRequests;
        this.objectMapper = objectMapper;
        this.cartReconciliationEnabled = cartReconciliationEnabled;
    }

    @Override
    @Transactional
    public RegularHoldConfirmationResult apply(RegularStockHoldConfirmedCommand command) {
        Objects.requireNonNull(command, "command");
        PurchaseSagaJpaEntity storedSaga = sagas.findLockedByOrderId(command.orderId())
                .orElseThrow(() -> invalid("Order Saga does not exist"));
        OrderJpaEntity storedOrder = orders.findLockedById(command.orderId())
                .orElseThrow(() -> invalid("Order does not exist"));
        var existing = inbox.findByEventId(command.eventId());
        if (existing.isPresent()) {
            if (existing.get().getPayloadFingerprint().equals(command.fingerprint())) {
                return RegularHoldConfirmationResult.replayed(command.orderId(), storedSaga.getId());
            }
            return RegularHoldConfirmationResult.conflict(command.orderId(), storedSaga.getId(),
                    "RegularStockHoldConfirmed event identity was reused");
        }
        validateIdentityAndSnapshot(command, storedSaga, storedOrder);
        var latest = inbox.findFirstByOrderIdAndAggregateIdAndProducerOrderByAggregateVersionDesc(
                command.orderId(), command.holdId(), "inventory-service");
        if (latest.isPresent() && command.aggregateVersion() < latest.get().getAggregateVersion()) {
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldConfirmed(command));
            return RegularHoldConfirmationResult.stale(command.orderId(), storedSaga.getId());
        }
        if (latest.isPresent() && command.aggregateVersion() == latest.get().getAggregateVersion()) {
            if (storedSaga.getStatus() != PurchaseSagaStatus.COMPLETED
                    || !latest.get().getProcessedAt().equals(command.transitionedAt())) {
                throw invalid("regular hold version conflicts with its recorded terminal state");
            }
            // A new command can report the same terminal hold without changing stock or Order again.
            inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldConfirmed(command));
            return RegularHoldConfirmationResult.replayed(command.orderId(), storedSaga.getId());
        }
        if (storedSaga.getStatus() != PurchaseSagaStatus.CONFIRMING_STOCK) {
            throw invalid("regular hold confirmation requires a confirming Order Saga");
        }
        if (!command.causationId().equals(storedSaga.getActiveCommandId())) {
            throw invalid("regular hold confirmation does not match the active command");
        }
        PurchaseSaga completed = restore(storedSaga).completeRegularStock(command.holdId(), command.paymentId(),
                command.transitionedAt());
        storedOrder.confirm(command.transitionedAt());
        storedSaga.apply(completed);
        sagas.saveAndFlush(storedSaga);
        orders.saveAndFlush(storedOrder);
        inbox.saveAndFlush(PurchaseSagaInboxJpaEntity.regularStockHoldConfirmed(command));
        outbox.saveAndFlush(OrderCreationOutboxJpaEntity.regularOrderConfirmed(command, completed, storedOrder));
        if (cartReconciliationEnabled
                && storedOrder.getPurchaseSource() == com.philia.flashsale.order.order.domain.model.PurchaseSource.CART) {
            if (regularRequests == null) {
                throw invalid("Cart reconciliation persistence is not configured");
            }
            RegularPurchaseRequestJpaEntity request = regularRequests.findById(command.purchaseRequestId())
                    .orElseThrow(() -> invalid("Cart purchase intake does not exist"));
            outbox.saveAndFlush(OrderCreationOutboxJpaEntity.reconcilePurchasedCart(command, completed,
                    storedOrder, request.getSnapshotPayload(), objectMapper));
        }
        return RegularHoldConfirmationResult.applied(command.orderId(), storedSaga.getId());
    }

    private void validateIdentityAndSnapshot(RegularStockHoldConfirmedCommand command,
            PurchaseSagaJpaEntity saga, OrderJpaEntity order) {
        if (saga.getStockParticipantType() != StockParticipantType.REGULAR_STOCK_HOLD
                || order.getStockParticipantType() != StockParticipantType.REGULAR_STOCK_HOLD
                || !saga.getId().equals(command.sagaId())
                || !saga.getPurchaseRequestId().equals(command.purchaseRequestId())
                || !saga.getOrderId().equals(command.orderId())
                || !saga.getStockReferenceId().equals(command.holdId())
                || !order.getId().equals(command.orderId())
                || !order.getPurchaseRequestId().equals(command.purchaseRequestId())
                || !order.getStockReferenceId().equals(command.holdId())) {
            throw invalid("regular hold confirmation identity conflicts with Order Saga");
        }
        if (saga.getPaymentId() == null || !saga.getPaymentId().equals(command.paymentId())) {
            throw invalid("payment identity conflicts with Order Saga");
        }
        Map<UUID, Long> expected = lines.findByOrder_IdOrderByIdAsc(order.getId()).stream()
                .collect(Collectors.toMap(line -> line.getVariantId(), line -> line.getQuantity()));
        Map<UUID, Long> actual = command.items().stream()
                .collect(Collectors.toMap(item -> item.variantId(), item -> item.quantity()));
        if (expected.isEmpty() || !expected.equals(actual)) {
            throw invalid("regular hold confirmation items conflict with immutable Order lines");
        }
    }

    private PurchaseSaga restore(PurchaseSagaJpaEntity entity) {
        return PurchaseSaga.restoreRegular(entity.getId(), entity.getOrderId(), entity.getPurchaseRequestId(),
                entity.getStockReferenceId(), entity.getStatus(), entity.getPaymentDeadline(), entity.getPaymentId(),
                entity.getLastPaymentVersion(), entity.getPaymentSucceededAt(), entity.getPaymentFailureReason(),
                entity.getDesiredOrderStatus(), entity.getManualReviewReason(), entity.getActiveCommandId(),
                entity.getStepStartedAt(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private InvalidRegularHoldConfirmationException invalid(String message) {
        return new InvalidRegularHoldConfirmationException(message);
    }
}
