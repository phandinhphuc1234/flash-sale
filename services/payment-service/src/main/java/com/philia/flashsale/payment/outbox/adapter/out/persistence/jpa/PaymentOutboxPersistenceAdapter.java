package com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository.PaymentOutboxEventJpaRepository;
import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for stable Payment fact rows and PostgreSQL lease claims. */
@Component
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentOutboxPersistenceAdapter implements SavePaymentOutboxPort, ClaimPaymentOutboxPort {

    private final PaymentOutboxEventJpaRepository repository;

    public PaymentOutboxPersistenceAdapter(PaymentOutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public OutboxRecord save(OutboxRecord record) {
        var entity = PaymentOutboxEventJpaEntity.pending(record.eventId(), record.aggregateId(),
                record.aggregateVersion(), record.eventType(), record.eventVersion(), record.topicName(),
                record.messageKey(), record.payload(), record.traceparent(), record.tracestate(), record.createdAt());
        return toRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public List<OutboxRecord> claimBatch(Instant now, int batchSize, String leaseOwner,
            Instant leaseUntil) {
        return repository.claimCandidates(now, batchSize).stream().peek(row -> row.claim(leaseOwner, leaseUntil))
                .map(row -> repository.save(row)).map(this::toRecord).toList();
    }

    private OutboxRecord toRecord(PaymentOutboxEventJpaEntity entity) {
        return new OutboxRecord(entity.getEventId(), entity.getAggregateId(), entity.getAggregateVersion(),
                entity.getEventType(), entity.getEventVersion(), entity.getTopicName(), entity.getMessageKey(),
                entity.getPayload(), entity.getTraceparent(), entity.getTracestate(), entity.getStatus(),
                entity.getAttemptCount(), entity.getNextAttemptAt(), entity.getLeaseOwner(), entity.getLeaseUntil(),
                entity.getPublishedAt(), entity.getCreatedAt());
    }
}
