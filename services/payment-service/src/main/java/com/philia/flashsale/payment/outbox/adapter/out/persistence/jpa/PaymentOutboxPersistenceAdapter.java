package com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository.PaymentOutboxEventJpaRepository;
import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.util.ArrayList;
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
    public OutboxRecord save(OutboxRecord outboxRecord) {
        var entity = PaymentOutboxEventJpaEntity.pending(outboxRecord.eventId(), outboxRecord.aggregateId(),
                outboxRecord.aggregateVersion(), outboxRecord.eventType(), outboxRecord.eventVersion(),
                outboxRecord.topicName(), outboxRecord.messageKey(), outboxRecord.payload(),
                outboxRecord.traceparent(), outboxRecord.tracestate(), outboxRecord.createdAt());
        return toOutboxRecord(repository.save(entity));
    }

    @Override
    @Transactional
    public List<OutboxRecord> claimBatch(Instant now, int batchSize, String leaseOwner,
            Instant leaseUntil) {
        List<OutboxRecord> claimed = new ArrayList<>();
        for (var outboxEntity : repository.claimCandidates(now, batchSize)) {
            outboxEntity.claim(leaseOwner, leaseUntil);
            claimed.add(toOutboxRecord(repository.save(outboxEntity)));
        }
        return claimed;
    }

    private OutboxRecord toOutboxRecord(PaymentOutboxEventJpaEntity entity) {
        return new OutboxRecord(entity.getEventId(), entity.getAggregateId(), entity.getAggregateVersion(),
                entity.getEventType(), entity.getEventVersion(), entity.getTopicName(), entity.getMessageKey(),
                entity.getPayload(), entity.getTraceparent(), entity.getTracestate(), entity.getStatus(),
                entity.getAttemptCount(), entity.getNextAttemptAt(), entity.getLeaseOwner(), entity.getLeaseUntil(),
                entity.getPublishedAt(), entity.getCreatedAt());
    }
}
