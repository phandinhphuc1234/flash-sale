package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.allocation.application.port.out.RecordAllocationOutboxPort;
import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity.OutboxEventJpaEntity;
import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.repository.OutboxEventJpaRepository;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactOutboxEvent;
import com.philia.flashsale.inventory.regularhold.application.port.out.RecordRegularHoldOutboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.ReplayRegularHoldOutboxPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxPersistenceAdapter implements RecordAllocationOutboxPort, RecordRegularHoldOutboxPort,
        ReplayRegularHoldOutboxPort {
    private final OutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPersistenceAdapter(OutboxEventJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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

    @Override
    public void record(RegularHoldFactOutboxEvent event) {
        try {
            repository.save(new OutboxEventJpaEntity(event.eventId(), "REGULAR_STOCK_HOLD", event.holdId(),
                    event.eventType(), objectMapper.writeValueAsString(event), event.aggregateVersion(), 1,
                    event.orderId().toString(), event.purchaseRequestId(), event.causationId(), event.traceparent(),
                    event.tracestate(), event.transitionedAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist regular hold fact", exception);
        }
    }

    @Override
    public void requeue(UUID eventId, Instant now) {
        repository.findById(eventId)
                .filter(event -> "REGULAR_STOCK_HOLD".equals(event.getAggregateType()))
                .ifPresent(event -> event.requeue(now));
    }
}
