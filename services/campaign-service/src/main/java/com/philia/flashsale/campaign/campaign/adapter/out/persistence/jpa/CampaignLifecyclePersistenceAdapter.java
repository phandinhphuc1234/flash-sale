package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.mapper.CampaignPersistenceMapper;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadDueCampaignsPort;
import com.philia.flashsale.campaign.campaign.application.port.out.SaveCampaignLifecycleEventPort;
import com.philia.flashsale.campaign.campaign.application.port.out.TransitionCampaignPort;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignActivated;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists one-winner lifecycle transitions and the activation outbox atomically. */
@Repository
public class CampaignLifecyclePersistenceAdapter implements LoadDueCampaignsPort, TransitionCampaignPort {

    private final CampaignJpaRepository campaignRepository;
    private final CampaignPersistenceMapper mapper;
    private final SaveCampaignLifecycleEventPort lifecycleEventPort;

    public CampaignLifecyclePersistenceAdapter(
            CampaignJpaRepository campaignRepository,
            CampaignPersistenceMapper mapper,
            SaveCampaignLifecycleEventPort lifecycleEventPort) {
        this.campaignRepository = campaignRepository;
        this.mapper = mapper;
        this.lifecycleEventPort = lifecycleEventPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> findDueForActivation(Instant now, int limit) {
        return campaignRepository.findDueForActivation(
                        CampaignStatus.SCHEDULED, now, PageRequest.of(0, positiveLimit(limit)))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> findDueForEnding(Instant now, int limit) {
        return campaignRepository.findDueForEnding(
                        CampaignStatus.ACTIVE, now, PageRequest.of(0, positiveLimit(limit)))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Optional<Campaign> activate(Campaign candidate, ActivateCampaignCommand command) {
        int updated = campaignRepository.activateIfDue(
                candidate.id(), CampaignStatus.SCHEDULED, command.expectedVersion(),
                command.now(), command.actor());
        if (updated != 1) {
            return Optional.empty();
        }

        UUID eventId = UUID.nameUUIDFromBytes(
                (candidate.id() + ":CampaignActivated:v1").getBytes(StandardCharsets.UTF_8));
        CampaignActivated event = CampaignActivated.of(eventId, candidate, command.now());
        lifecycleEventPort.saveActivated(event, command.traceId(), command.now());
        return Optional.of(candidate);
    }

    @Override
    @Transactional
    public Optional<Campaign> end(Campaign candidate, EndCampaignCommand command) {
        int updated = campaignRepository.endIfDue(
                candidate.id(), CampaignStatus.ACTIVE, command.expectedVersion(),
                command.now(), command.actor());
        return updated == 1 ? Optional.of(candidate) : Optional.empty();
    }

    private int positiveLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Lifecycle batch size must be positive");
        }
        return limit;
    }
}
