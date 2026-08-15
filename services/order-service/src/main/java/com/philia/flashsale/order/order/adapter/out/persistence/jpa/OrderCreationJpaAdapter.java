package com.philia.flashsale.order.order.adapter.out.persistence.jpa;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderConsumerInboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.mapper.OrderPersistenceMapper;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderConsumerInboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.order.application.port.out.PersistOrderCreationPort;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter that arbitrates identities and commits four durable rows atomically. */
public class OrderCreationJpaAdapter implements PersistOrderCreationPort {

    private final OrderJpaRepository orders;
    private final OrderConsumerInboxJpaRepository inbox;
    private final OrderCreationOutboxJpaRepository outbox;
    private final OrderPersistenceMapper mapper;
    private final EntityManager entityManager;

    public OrderCreationJpaAdapter(OrderJpaRepository orders, OrderConsumerInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox, EntityManager entityManager) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.mapper = new OrderPersistenceMapper();
    }

    @Override
    @Transactional
    public OrderCreationResult persist(OrderCreationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        lockIdentities(candidate.order().purchaseRequestId(), candidate.order().reservationId());

        Optional<OrderConsumerInboxJpaEntity> event = inbox.findById(candidate.eventId());
        if (event.isPresent()) {
            return eventResult(event.get(), candidate);
        }

        Optional<OrderJpaEntity> purchaseOrder = orders.findByPurchaseRequestId(candidate.order().purchaseRequestId());
        Optional<OrderJpaEntity> reservationOrder = orders.findByReservationId(candidate.order().reservationId());
        if (purchaseOrder.isPresent() || reservationOrder.isPresent()) {
            OrderJpaEntity established = purchaseOrder.orElseGet(reservationOrder::get);
            if (purchaseOrder.isPresent() && reservationOrder.isPresent()
                    && !purchaseOrder.get().getId().equals(reservationOrder.get().getId())) {
                return OrderCreationResult.conflict(established.getId(), candidate.fingerprint(),
                        "purchase and reservation identities belong to different Orders");
            }
            Optional<OrderConsumerInboxJpaEntity> establishedInbox = inbox
                    .findFirstByPurchaseRequestId(candidate.order().purchaseRequestId())
                    .or(() -> inbox.findFirstByReservationId(candidate.order().reservationId()));
            if (establishedInbox.isPresent() && establishedInbox.get().getPayloadFingerprint()
                    .equals(candidate.fingerprint())) {
                return OrderCreationResult.businessReplayed(established.getId(), candidate.fingerprint());
            }
            if (establishedInbox.isEmpty() && sameSnapshot(established, candidate)) {
                return OrderCreationResult.businessReplayed(established.getId(), candidate.fingerprint());
            }
            return OrderCreationResult.conflict(established.getId(), candidate.fingerprint(),
                    "accepted-purchase identity conflicts with established Order");
        }

        try {
            OrderJpaEntity order = mapper.order(candidate);
            orders.saveAndFlush(order);
            // Keep the aggregate and line representations separate even though this MVP has one line.
            var line = mapper.line(candidate, order);
            entityManager.persist(line);
            entityManager.flush();
            inbox.saveAndFlush(mapper.inbox(candidate, order));
            outbox.saveAndFlush(mapper.outbox(candidate));
            return OrderCreationResult.created(order.getId(), candidate.outboxEventId(), candidate.fingerprint());
        } catch (DataAccessException exception) {
            throw new RetryableOrderPersistenceException("Order persistence failed before commit", exception);
        }
    }

    private OrderCreationResult eventResult(OrderConsumerInboxJpaEntity existing,
            OrderCreationCandidate candidate) {
        UUID orderId = existing.getOrder().getId();
        if (existing.getPayloadFingerprint().equals(candidate.fingerprint())) {
            return OrderCreationResult.eventReplayed(orderId, candidate.fingerprint());
        }
        return OrderCreationResult.conflict(orderId, candidate.fingerprint(),
                "event identity was reused with contradictory business content");
    }

    private void lockIdentities(UUID purchaseRequestId, UUID reservationId) {
        long[] keys = { PostgreSqlOrderIdentityLockKey.forPurchaseRequest(purchaseRequestId),
                PostgreSqlOrderIdentityLockKey.forReservation(reservationId) };
        Arrays.stream(keys).distinct().boxed().sorted(Comparator.naturalOrder()).forEach(this::lock);
    }

    private void lock(long key) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(cast(?1 as bigint))")
                .setParameter(1, key)
                .getSingleResult();
    }

    private boolean sameSnapshot(OrderJpaEntity existing, OrderCreationCandidate candidate) {
        var expected = candidate.order();
        return existing.getPurchaseRequestId().equals(expected.purchaseRequestId())
                && existing.getReservationId().equals(expected.reservationId())
                && existing.getCampaignId().equals(expected.campaignId())
                && existing.getUserId().equals(expected.userId())
                && existing.getCurrency().equals(expected.currency())
                && existing.getSubtotalAmount().compareTo(expected.subtotal().amount()) == 0
                && existing.getTotalAmount().compareTo(expected.total().amount()) == 0
                && existing.getAcceptedAt().equals(expected.acceptedAt())
                && existing.getReservationExpiresAt().equals(expected.reservationExpiresAt());
    }
}
