package com.philia.flashsale.order.order.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderConsumerInboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderLineJpaEntity;
import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import java.time.Instant;

/** Explicit adapter-local mapper; it keeps JPA representations out of the application core. */
public class OrderPersistenceMapper {

    public OrderJpaEntity order(OrderCreationCandidate candidate) {
        return OrderJpaEntity.from(candidate.order(), candidate.createdAt());
    }

    public OrderLineJpaEntity line(OrderCreationCandidate candidate, OrderJpaEntity order) {
        return OrderLineJpaEntity.from(candidate.order().line(), order, candidate.createdAt());
    }

    public OrderConsumerInboxJpaEntity inbox(OrderCreationCandidate candidate, OrderJpaEntity order) {
        return OrderConsumerInboxJpaEntity.from(candidate, order);
    }

    public OrderCreationOutboxJpaEntity outbox(OrderCreationCandidate candidate) {
        return OrderCreationOutboxJpaEntity.from(candidate);
    }

    public OrderCreationOutboxJpaEntity paymentRequestedOutbox(OrderCreationCandidate candidate) {
        return OrderCreationOutboxJpaEntity.paymentRequested(candidate);
    }

    public Instant createdAt(OrderCreationCandidate candidate) {
        return candidate.createdAt();
    }
}
