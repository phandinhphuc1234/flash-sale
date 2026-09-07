package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity.OutboxEventJpaEntity;
import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.repository.OutboxEventJpaRepository;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldOutboxEvent;
import com.philia.flashsale.inventory.regularhold.application.port.out.RegularHoldOutboxDispatchPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of leased claim/retry publication state for regular-hold facts only. */
@Component
public class RegularHoldOutboxDispatchPersistenceAdapter implements RegularHoldOutboxDispatchPort {
    private static final String REGULAR_STOCK_HOLD_AGGREGATE = "REGULAR_STOCK_HOLD";
    private final OutboxEventJpaRepository repository;

    public RegularHoldOutboxDispatchPersistenceAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public List<RegularHoldOutboxEvent> claimDue(String workerId, Instant now, Duration lease, int batchSize) {
        Instant until = now.plus(lease);
        return repository.findDueForClaim(REGULAR_STOCK_HOLD_AGGREGATE, now, PageRequest.of(0, batchSize)).stream()
                .peek(event -> event.claim(workerId, until))
                .map(RegularHoldOutboxDispatchPersistenceAdapter::toModel)
                .toList();
    }

    @Override
    @Transactional
    public boolean markPublished(UUID eventId, String workerId, Instant now) {
        return repository.findById(eventId).filter(event -> event.isClaimedBy(workerId)).map(event -> {
            event.markPublished(now);
            return true;
        }).orElse(false);
    }

    @Override
    @Transactional
    public boolean recordFailure(UUID eventId, String workerId, Instant now, List<Duration> retryDelays) {
        return repository.findById(eventId).filter(event -> event.isClaimedBy(workerId)).map(event -> {
            int retryIndex = event.getRetryCount();
            boolean terminal = retryIndex >= retryDelays.size();
            Duration delay = terminal ? Duration.ZERO : retryDelays.get(retryIndex);
            event.recordFailure(now.plus(delay), terminal);
            return true;
        }).orElse(false);
    }

    private static RegularHoldOutboxEvent toModel(OutboxEventJpaEntity event) {
        return new RegularHoldOutboxEvent(event.getId(), event.getEventType(), event.getAggregateVersion(),
                event.getAggregateId(), event.getEventKey(), event.getCorrelationId(), event.getCausationId(),
                event.getTraceparent(), event.getTracestate(), event.getPayload());
    }
}
