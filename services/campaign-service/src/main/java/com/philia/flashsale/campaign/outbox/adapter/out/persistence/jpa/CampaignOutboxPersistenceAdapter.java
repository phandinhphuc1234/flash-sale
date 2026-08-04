package com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.campaign.campaign.application.port.out.SaveCampaignLifecycleEventPort;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignActivated;
import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.entity.CampaignOutboxEventJpaEntity;
import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.repository.CampaignOutboxEventJpaRepository;
import com.philia.flashsale.campaign.outbox.application.model.CampaignOutboxEvent;
import com.philia.flashsale.campaign.outbox.application.port.out.SaveCampaignOutboxEventPort;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/** Owns outbox serialization and persistence while exposing application-level ports only. */
@Repository
public class CampaignOutboxPersistenceAdapter
        implements SaveCampaignOutboxEventPort, SaveCampaignLifecycleEventPort {

    private final CampaignOutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    public CampaignOutboxPersistenceAdapter(
            CampaignOutboxEventJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(CampaignOutboxEvent event) {
        repository.save(toEntity(event));
    }

    @Override
    public void saveActivated(CampaignActivated event, String traceId, Instant createdAt) {
        save(new CampaignOutboxEvent(
                event.eventId(), event.aggregateId(), event.aggregateVersion(), event.eventType(),
                event.eventVersion(), event.aggregateId().toString(), serialize(event), traceId,
                event.occurredAt(), createdAt));
    }

    private CampaignOutboxEventJpaEntity toEntity(CampaignOutboxEvent source) {
        return new CampaignOutboxEventJpaEntity(
                source.id(), source.aggregateId(), source.aggregateVersion(), source.eventType(),
                source.eventVersion(), source.eventKey(), source.payload(), "PENDING", 0,
                source.createdAt(), null, null, source.occurredAt(), null, null, 0, null, null,
                source.traceId(), source.createdAt(), source.createdAt());
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Campaign lifecycle event could not be serialized", exception);
        }
    }
}
