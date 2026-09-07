package com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderLineJpaEntity;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.entity.RegularPurchaseRequestJpaEntity;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.mapper.RegularPurchasePersistenceMapper;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.repository.RegularPurchaseRequestJpaRepository;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseRecoveryClaim;
import com.philia.flashsale.order.regularpurchase.application.port.out.ClaimRegularPurchaseRecoveryPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL adapter for Order-owned regular intake checkpoints and the atomic accepted commit.
 *
 * <p>No Product, Cart, Inventory, or Payment call is made inside this transaction. An accepted
 * regular hold is therefore never followed by a partly durable Order/Saga/outbox state.</p>
 */
public class RegularPurchasePersistenceAdapter implements PersistRegularPurchasePort, ClaimRegularPurchaseRecoveryPort {

    private final RegularPurchaseRequestJpaRepository requests;
    private final OrderJpaRepository orders;
    private final PurchaseSagaJpaRepository sagas;
    private final OrderCreationOutboxJpaRepository outbox;
    private final EntityManager entityManager;
    private final RegularPurchasePersistenceMapper mapper;

    public RegularPurchasePersistenceAdapter(RegularPurchaseRequestJpaRepository requests,
            OrderJpaRepository orders, PurchaseSagaJpaRepository sagas,
            OrderCreationOutboxJpaRepository outbox, EntityManager entityManager, ObjectMapper objectMapper) {
        this.requests = requests;
        this.orders = orders;
        this.sagas = sagas;
        this.outbox = outbox;
        this.entityManager = entityManager;
        this.mapper = new RegularPurchasePersistenceMapper(objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RegularPurchaseRequest> findByShopperAndIdempotencyKey(UUID shopperId, String idempotencyKey) {
        return requests.findByShopperIdAndIdempotencyKey(shopperId, idempotencyKey).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public RegularPurchaseRequest register(RegularPurchaseRequest request) {
        lockIdempotencyIdentity(request.shopperId(), request.idempotencyKey());
        Optional<RegularPurchaseRequestJpaEntity> existing = requests
                .findByShopperIdAndIdempotencyKey(request.shopperId(), request.idempotencyKey());
        if (existing.isPresent()) return mapper.toDomain(existing.get());
        RegularPurchaseRequestJpaEntity entity = RegularPurchaseRequestJpaEntity.create();
        mapper.apply(entity, request, null, null);
        requests.saveAndFlush(entity);
        return request;
    }

    @Override
    @Transactional
    public RegularPurchaseRequest update(RegularPurchaseRequest request) {
        RegularPurchaseRequestJpaEntity entity = requests.findById(request.id())
                .orElseThrow(() -> new IllegalStateException("regular purchase intake does not exist"));
        mapper.apply(entity, request, entity.getTraceparent(), entity.getTracestate());
        requests.saveAndFlush(entity);
        return request;
    }

    @Override
    @Transactional
    public RegularPurchaseRequest accept(RegularPurchaseAcceptance acceptance) {
        try {
            RegularPurchaseRequestJpaEntity intake = requests.findById(acceptance.intake().id())
                    .orElseThrow(() -> new IllegalStateException("regular purchase intake does not exist"));
            lockIdempotencyIdentity(acceptance.intake().shopperId(), acceptance.intake().idempotencyKey());
            if (intake.getOrderId() != null) return mapper.toDomain(intake);

            OrderJpaEntity order = OrderJpaEntity.from(acceptance.order(), acceptance.occurredAt());
            orders.saveAndFlush(order);
            for (var line : acceptance.order().lines()) {
                entityManager.persist(OrderLineJpaEntity.from(line, order, acceptance.occurredAt()));
            }
            entityManager.flush();
            sagas.saveAndFlush(PurchaseSagaJpaEntity.from(acceptance.saga()));
            outbox.saveAndFlush(OrderCreationOutboxJpaEntity.regularOrderCreated(acceptance.orderCreatedEventId(),
                    acceptance.order(), acceptance.saga(), acceptance.correlationId(), acceptance.causationId(),
                    acceptance.traceparent(), acceptance.tracestate(), acceptance.occurredAt()));
            outbox.saveAndFlush(OrderCreationOutboxJpaEntity.paymentRequested(acceptance.paymentRequestedEventId(),
                    acceptance.order(), acceptance.saga(), acceptance.correlationId(), acceptance.causationId(),
                    acceptance.traceparent(), acceptance.tracestate(), acceptance.occurredAt()));
            mapper.apply(intake, acceptance.intake(), acceptance.traceparent(), acceptance.tracestate());
            requests.saveAndFlush(intake);
            return acceptance.intake();
        } catch (DataAccessException exception) {
            throw new IllegalStateException("regular purchase acceptance could not commit atomically", exception);
        }
    }

    /**
     * Claims stale rows in one short transaction. The row locks end before Product/Cart/Inventory
     * calls begin; the persisted lease is what prevents another Order instance from taking them.
     */
    @Override
    @Transactional
    public List<RegularPurchaseRecoveryClaim> claim(String workerId, Instant now, int batchSize, Duration lease) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 128) {
            throw new IllegalArgumentException("workerId must be one to 128 characters");
        }
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be between 1 and 500");
        if (lease == null || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be positive");
        Instant staleBefore = now.minus(lease);
        @SuppressWarnings("unchecked")
        List<Object> rawIds = entityManager.createNativeQuery("""
                select id
                  from regular_purchase_requests
                 where state in ('RECEIVED', 'SNAPSHOT_VALIDATED', 'PRODUCT_VALIDATED', 'HOLD_ACQUIRED')
                   and updated_at <= ?1
                   and (recovery_lease_until is null or recovery_lease_until <= ?2)
                 order by updated_at, id
                 for update skip locked
                """)
                .setParameter(1, staleBefore)
                .setParameter(2, now)
                .setMaxResults(batchSize)
                .getResultList();
        if (rawIds.isEmpty()) return List.of();
        Instant leaseUntil = now.plus(lease);
        List<RegularPurchaseRecoveryClaim> claims = new ArrayList<>(rawIds.size());
        for (Object rawId : rawIds) {
            UUID id = rawId instanceof UUID uuid ? uuid : UUID.fromString(rawId.toString());
            RegularPurchaseRequestJpaEntity entity = requests.findById(id)
                    .orElseThrow(() -> new IllegalStateException("claimed regular purchase intake disappeared"));
            entity.claimRecoveryLease(workerId, leaseUntil);
            claims.add(new RegularPurchaseRecoveryClaim(mapper.toDomain(entity), workerId, leaseUntil));
        }
        requests.flush();
        return claims;
    }

    @Override
    @Transactional
    public void release(UUID requestId, String workerId) {
        requests.findById(requestId).ifPresent(entity -> {
            entity.releaseRecoveryLease(workerId);
            requests.saveAndFlush(entity);
        });
    }

    private void lockIdempotencyIdentity(UUID shopperId, String idempotencyKey) {
        UUID hash = UUID.nameUUIDFromBytes((shopperId + ":" + idempotencyKey)
                .getBytes(StandardCharsets.UTF_8));
        entityManager.createNativeQuery("select pg_advisory_xact_lock(cast(?1 as bigint))")
                .setParameter(1, hash.getMostSignificantBits())
                .getSingleResult();
    }
}
