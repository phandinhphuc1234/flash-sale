package com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa;

import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.entity.CampaignScheduleOperationJpaEntity;
import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.repository.CampaignScheduleOperationJpaRepository;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadLockedScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadResumableScheduleOperationsPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationFingerprint;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

/** JPA adapter translating durable schedule-operation rows to the framework-free domain model. */
@Repository
public class ScheduleOperationPersistenceAdapter implements
        LoadLockedScheduleOperationPort,
        LoadResumableScheduleOperationsPort,
        SaveScheduleOperationPort {

    private final CampaignScheduleOperationJpaRepository repository;

    public ScheduleOperationPersistenceAdapter(CampaignScheduleOperationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ScheduleOperation> findByCampaignIdAndIdempotencyKey(
            UUID campaignId, String idempotencyKey) {
        return repository.findByCampaignIdAndIdempotencyKey(campaignId, idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public Optional<ScheduleOperation> findLockedByCampaignIdAndIdempotencyKey(
            UUID campaignId, String idempotencyKey) {
        return repository.findLockedByCampaignIdAndIdempotencyKey(campaignId, idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public Optional<ScheduleOperation> findByInventoryRequestId(UUID inventoryRequestId) {
        return repository.findByInventoryRequestId(inventoryRequestId).map(this::toDomain);
    }

    @Override
    public boolean existsInFlightByCampaignId(UUID campaignId) {
        return repository.existsByCampaignIdAndOperationStatusIn(
                campaignId, List.of("STARTED", "INVENTORY_ALLOCATED"));
    }

    @Override
    public List<ScheduleOperation> findResumable(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return repository.findByOperationStatusInOrderByUpdatedAtAsc(
                        List.of("STARTED", "INVENTORY_ALLOCATED"), PageRequest.of(0, boundedLimit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public ScheduleOperation save(ScheduleOperation operation) {
        CampaignScheduleOperationJpaEntity entity = repository.findById(operation.id())
                .orElseGet(CampaignScheduleOperationJpaEntity::new);
        entity.setId(operation.id());
        entity.setCampaignId(operation.campaignId());
        entity.setIdempotencyKey(operation.idempotencyKey());
        entity.setInventoryRequestId(operation.inventoryRequestId());
        entity.setRequestHash(operation.requestHash());
        entity.setCampaignVersion(operation.campaignVersion());
        entity.setOperationStatus(operation.status().name());
        entity.setAttemptCount(operation.attemptCount());
        entity.setLastFailureCode(operation.lastFailureCode());
        entity.setLastFailureMessage(operation.lastFailureMessage());
        entity.setInitiatedBy(operation.initiatedBy());
        entity.setCallerService(operation.callerService());
        entity.setTraceId(operation.traceId());
        entity.setCreatedAt(operation.createdAt());
        entity.setUpdatedAt(operation.updatedAt());
        return toDomain(repository.saveAndFlush(entity));
    }

    private ScheduleOperation toDomain(CampaignScheduleOperationJpaEntity entity) {
        return ScheduleOperation.rehydrate(
                entity.getId(),
                entity.getCampaignId(),
                entity.getIdempotencyKey(),
                entity.getInventoryRequestId(),
                new ScheduleOperationFingerprint(entity.getRequestHash()),
                entity.getCampaignVersion(),
                ScheduleOperationStatus.valueOf(entity.getOperationStatus()),
                entity.getAttemptCount(),
                entity.getLastFailureCode(),
                entity.getLastFailureMessage(),
                entity.getInitiatedBy(),
                entity.getCallerService(),
                entity.getTraceId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
