package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignItemJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.mapper.CampaignPersistenceMapper;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.application.command.FinalizeCampaignSchedulingCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignVersionConflictException;
import com.philia.flashsale.campaign.campaign.application.port.out.FinalizeCampaignSchedulingPort;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignScheduled;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import com.philia.flashsale.campaign.outbox.application.model.CampaignOutboxEvent;
import com.philia.flashsale.campaign.outbox.application.port.out.SaveCampaignOutboxEventPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadLockedScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Commits the Campaign snapshot, lifecycle state, operation, and outbox row atomically. */
@Repository
public class CampaignSchedulingPersistenceAdapter implements FinalizeCampaignSchedulingPort {

    private final CampaignJpaRepository campaignRepository;
    private final CampaignPersistenceMapper mapper;
    private final LoadLockedScheduleOperationPort operationLoader;
    private final SaveScheduleOperationPort operationSaver;
    private final SaveCampaignOutboxEventPort outboxSaver;
    private final ObjectMapper objectMapper;

    public CampaignSchedulingPersistenceAdapter(
            CampaignJpaRepository campaignRepository,
            CampaignPersistenceMapper mapper,
            LoadLockedScheduleOperationPort operationLoader,
            SaveScheduleOperationPort operationSaver,
            SaveCampaignOutboxEventPort outboxSaver,
            ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.mapper = mapper;
        this.operationLoader = operationLoader;
        this.operationSaver = operationSaver;
        this.outboxSaver = outboxSaver;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Campaign finalizeSchedule(FinalizeCampaignSchedulingCommand command) {
        CampaignJpaEntity entity = campaignRepository.findDetailedByIdForUpdate(command.campaignId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign was not found"));
        ScheduleOperation operation = operationLoader.findLockedById(command.operationId())
                .orElseThrow(() -> new IllegalArgumentException("Schedule operation was not found"));
        if (!command.campaignId().equals(operation.campaignId())) {
            throw new IllegalArgumentException("Schedule operation does not belong to Campaign");
        }
        if (operation.status() == ScheduleOperationStatus.COMPLETED) {
            return mapper.toDomain(entity);
        }

        Campaign current = mapper.toDomain(entity);
        if (current.version() != command.expectedVersion()) {
            throw new CampaignVersionConflictException(
                    current.id(), command.expectedVersion(), current.version());
        }
        if (!current.isEditable()) {
            throw new IllegalStateException("Only a draft Campaign can be scheduled");
        }
        CampaignItem snapshot = current.item()
                .withProductSnapshot(
                        command.product().productId(), command.product().sku(),
                        CampaignMoney.vnd(command.product().basePrice()))
                .withInventoryAllocation(
                        command.allocation().inventoryAllocationId(),
                        command.allocation().allocatedQuantity());
        current.applySchedulingSnapshot(snapshot);
        current.markScheduled(command.actor(), command.now());

        mapper.updateEntity(current, entity);
        synchronizeItem(current.item(), entity);
        CampaignJpaEntity saved = campaignRepository.saveAndFlush(entity);

        operation.markCompleted();
        operationSaver.save(operation);

        java.util.UUID eventId = java.util.UUID.nameUUIDFromBytes(
                (operation.id() + ":CampaignScheduled:v1")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CampaignScheduled event = CampaignScheduled.of(eventId, current, command.now());
        CampaignOutboxEvent outbox = new CampaignOutboxEvent(
                eventId, current.id(), current.version(), event.eventType(), event.eventVersion(),
                current.id().toString(), serialize(event), command.traceId(), event.occurredAt(), command.now());
        outboxSaver.save(outbox);
        return mapper.toDomain(saved);
    }

    private void synchronizeItem(CampaignItem item, CampaignJpaEntity entity) {
        CampaignItemJpaEntity target = entity.getItem();
        if (target == null) {
            entity.attachItem(mapper.toEntity(item));
        } else {
            mapper.updateEntity(item, target);
            entity.attachItem(target);
        }
    }

    private String serialize(CampaignScheduled event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Campaign scheduled event could not be serialized", exception);
        }
    }

}
