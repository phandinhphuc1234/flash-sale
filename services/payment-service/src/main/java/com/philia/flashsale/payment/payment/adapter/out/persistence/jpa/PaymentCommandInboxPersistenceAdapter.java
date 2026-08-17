package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentCommandInboxJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentCommandInboxJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.application.port.out.PaymentCommandInboxPort;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence boundary for PaymentRequested command identity.
 *
 * <p>The PostgreSQL transaction advisory lock serializes first acceptance for one Order even when
 * the inbox row does not exist yet. It is an adapter concern; application code only sees the
 * {@link PaymentCommandInboxPort} capability.
 */
@Component
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentCommandInboxPersistenceAdapter implements PaymentCommandInboxPort {

    private final PaymentCommandInboxJpaRepository inboxRepository;
    private final PaymentJpaRepository paymentRepository;
    private final EntityManager entityManager;

    public PaymentCommandInboxPersistenceAdapter(PaymentCommandInboxJpaRepository inboxRepository,
            PaymentJpaRepository paymentRepository, EntityManager entityManager) {
        this.inboxRepository = inboxRepository;
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void lockOrder(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(cast(?1 as text), 0))")
                .setParameter(1, orderId.toString())
                .getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Receipt> findByEventId(UUID eventId) {
        return inboxRepository.findById(eventId).map(this::toReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Receipt> findByOrderId(UUID orderId) {
        return inboxRepository.findByOrderId(orderId).map(this::toReceipt);
    }

    @Override
    @Transactional
    public Receipt receive(UUID eventId, String eventType, int eventVersion, UUID orderId,
            String payloadFingerprint, Instant receivedAt) {
        return toReceipt(inboxRepository.save(PaymentCommandInboxJpaEntity.received(eventId, eventType,
                eventVersion, orderId, payloadFingerprint, receivedAt)));
    }

    @Override
    @Transactional
    public void markProcessed(UUID eventId, UUID paymentId, Instant processedAt) {
        PaymentCommandInboxJpaEntity inbox = inboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("unknown Payment command event"));
        inbox.markProcessed(paymentRepository.getReferenceById(paymentId), processedAt);
        inboxRepository.save(inbox);
    }

    @Override
    @Transactional
    public void markConflicted(UUID eventId, Instant observedAt) {
        inboxRepository.findById(eventId).ifPresent(inbox -> {
            inbox.markConflicted(observedAt);
            inboxRepository.save(inbox);
        });
    }

    private Receipt toReceipt(PaymentCommandInboxJpaEntity entity) {
        return new Receipt(entity.getEventId(), entity.getEventType(), entity.getEventVersion(),
                entity.getOrderId(), entity.getPayloadFingerprint(),
                entity.getPayment() == null ? null : entity.getPayment().getId(),
                entity.getProcessingStatus(), entity.getReceivedAt(), entity.getProcessedAt());
    }
}
