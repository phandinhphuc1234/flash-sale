package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.allocation.application.port.out.RecordAllocationOutboxPort;
import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity.OutboxEventJpaEntity;
import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.repository.OutboxEventJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxPersistenceAdapter implements RecordAllocationOutboxPort {
    private final OutboxEventJpaRepository repository;

    public OutboxPersistenceAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    /** Records a pending event; Kafka publication remains deferred. */
    @Override
    public void record(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt) {
        repository.save(new OutboxEventJpaEntity(
                UUID.randomUUID(), aggregateType, aggregateId, eventType, payload, occurredAt));
    }
}
