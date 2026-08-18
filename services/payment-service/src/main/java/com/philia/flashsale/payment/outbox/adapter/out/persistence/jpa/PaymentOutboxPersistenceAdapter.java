package com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository.PaymentOutboxEventJpaRepository;
import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.UpdatePaymentOutboxPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for stable Payment fact rows and PostgreSQL lease claims. */
@Component
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentOutboxPersistenceAdapter implements SavePaymentOutboxPort, ClaimPaymentOutboxPort,
        UpdatePaymentOutboxPort {

    private final PaymentOutboxEventJpaRepository repository;

    public PaymentOutboxPersistenceAdapter(PaymentOutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public OutboxRecord save(OutboxRecord outboxRecord) {
        var entity = PaymentOutboxEventJpaEntity.pending(outboxRecord.eventId(), outboxRecord.aggregateId(),
                outboxRecord.aggregateVersion(), outboxRecord.eventType(), outboxRecord.eventVersion(),
                outboxRecord.topicName(), outboxRecord.messageKey(), outboxRecord.payload(),
                outboxRecord.traceparent(), outboxRecord.tracestate(), outboxRecord.createdAt());
        return toOutboxRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public List<PaymentOutboxEvent> claimBatch(Instant now, int batchSize, String leaseOwner,
            Instant leaseUntil) {
        List<PaymentOutboxEvent> claimed = new ArrayList<>();
        for (var outboxEntity : repository.claimCandidates(now, batchSize)) {
            outboxEntity.claim(leaseOwner, leaseUntil);
            claimed.add(toPaymentOutboxEvent(repository.save(outboxEntity)));
        }
        return claimed;
    }

    @Override
    @Transactional
    public boolean markPublished(java.util.UUID eventId, String leaseOwner, Instant publishedAt) {
        return repository.findLockedByEventId(eventId)
                .filter(entity -> entity.isOwnedBy(leaseOwner))
                .map(entity -> {
                    entity.markPublished(publishedAt);
                    return true;
                }).orElse(false);
    }

    @Override
    @Transactional
    public boolean recordFailure(java.util.UUID eventId, String leaseOwner, Instant failedAt,
            String sanitizedErrorCode, Instant nextAttemptAt) {
        return repository.findLockedByEventId(eventId)
                .filter(entity -> entity.isOwnedBy(leaseOwner))
                .map(entity -> {
                    entity.markRetry(leaseOwner, sanitizedErrorCode, nextAttemptAt);
                    return true;
                }).orElse(false);
    }

    private OutboxRecord toOutboxRecord(PaymentOutboxEventJpaEntity entity) {
        return new OutboxRecord(entity.getEventId(), entity.getAggregateId(), entity.getAggregateVersion(),
                entity.getEventType(), entity.getEventVersion(), entity.getTopicName(), entity.getMessageKey(),
                entity.getPayload(), entity.getTraceparent(), entity.getTracestate(), entity.getStatus(),
                entity.getAttemptCount(), entity.getNextAttemptAt(), entity.getLeaseOwner(), entity.getLeaseUntil(),
                entity.getPublishedAt(), entity.getCreatedAt());
    }

    private PaymentOutboxEvent toPaymentOutboxEvent(PaymentOutboxEventJpaEntity entity) {
        return new PaymentOutboxEvent(entity.getEventId(), entity.getAggregateId(), entity.getAggregateVersion(),
                entity.getEventType(), entity.getEventVersion(), entity.getTopicName(), entity.getMessageKey(),
                entity.getPayload(), entity.getTraceparent(), entity.getTracestate(), entity.getStatus(),
                entity.getAttemptCount(), entity.getNextAttemptAt(), entity.getLeaseOwner(), entity.getLeaseUntil(),
                entity.getPublishedAt(), entity.getCreatedAt(), entity.getLastErrorCode());
    }
}
