package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularHoldCommandInboxJpaEntity;
import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository.RegularHoldCommandInboxJpaRepository;
import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldCommandInboxEntry;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadRegularHoldCommandInboxPort;
import com.philia.flashsale.inventory.regularhold.application.port.out.SaveRegularHoldCommandInboxPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RegularHoldCommandInboxPersistenceAdapter implements LoadRegularHoldCommandInboxPort,
        SaveRegularHoldCommandInboxPort {
    private final RegularHoldCommandInboxJpaRepository repository;

    public RegularHoldCommandInboxPersistenceAdapter(RegularHoldCommandInboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RegularHoldCommandInboxEntry> findByCommandId(UUID commandId) {
        return repository.findById(commandId).map(this::toDomain);
    }

    @Override
    public Optional<RegularHoldCommandInboxEntry> findBySourcePosition(String topic, int partition, long offset) {
        return repository.findBySourceTopicAndSourcePartitionAndSourceOffset(topic, partition, offset)
                .map(this::toDomain);
    }

    @Override
    public RegularHoldCommandInboxEntry save(RegularHoldCommandInboxEntry entry) {
        repository.save(new RegularHoldCommandInboxJpaEntity(entry.commandId(), entry.commandType(), entry.orderId(),
                entry.holdId(), entry.purchaseRequestId(), entry.aggregateVersion(), entry.payloadFingerprint(),
                entry.resultEventId(), entry.sourceTopic(), entry.sourcePartition(), entry.sourceOffset(),
                entry.processedAt()));
        return entry;
    }

    private RegularHoldCommandInboxEntry toDomain(RegularHoldCommandInboxJpaEntity entity) {
        return new RegularHoldCommandInboxEntry(entity.getCommandId(), entity.getCommandType(), entity.getOrderId(),
                entity.getHoldId(), entity.getPurchaseRequestId(), entity.getAggregateVersion(),
                entity.getPayloadFingerprint(), entity.getResultEventId(), entity.getSourceTopic(),
                entity.getSourcePartition(), entity.getSourceOffset(), entity.getProcessedAt());
    }
}
