package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignItemJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.mapper.CampaignPersistenceMapper;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.application.port.out.CheckCampaignCodeUniquenessPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignPort;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadCampaignSnapshotPort;
import com.philia.flashsale.campaign.campaign.application.port.out.SaveCampaignPort;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Persistence adapter for the Campaign aggregate.
 *
 * <p>The adapter keeps JPA entities and mapping at the outbound boundary while
 * exposing only campaign-owned application ports.</p>
 */
@Repository
public class CampaignPersistenceAdapter implements
        LoadCampaignPort,
        LoadCampaignSnapshotPort,
        SaveCampaignPort,
        CheckCampaignCodeUniquenessPort {

    private final CampaignJpaRepository repository;
    private final CampaignPersistenceMapper mapper;

    public CampaignPersistenceAdapter(
            CampaignJpaRepository repository,
            CampaignPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Loads the aggregate with its one-item MVP relation and maps it back to the domain model. */
    @Override
    public Optional<Campaign> findById(UUID campaignId) {
        return repository.findDetailedById(campaignId).map(mapper::toDomain);
    }

    /** Returns only a non-draft aggregate with a complete Product and Inventory snapshot. */
    @Override
    public Optional<Campaign> findCompleteSnapshotById(UUID campaignId) {
        return findById(campaignId)
                .filter(campaign -> !campaign.status().isEditable())
                .filter(Campaign::hasItem)
                .filter(campaign -> campaign.item().isReadyForScheduling());
    }

    /** Checks code uniqueness through the Campaign-owned repository only. */
    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCodeIgnoreCase(code);
    }

    /**
     * Persists a new aggregate or updates the existing aggregate and its optional item.
     *
     * <p>The mapper keeps JPA types at this outbound boundary and returns the
     * committed optimistic-lock version for HTTP ETag generation.</p>
     */
    @Override
    public Campaign save(Campaign campaign) {
        CampaignJpaEntity entity = repository.findDetailedById(campaign.id()).orElse(null);
        if (entity == null) {
            // New aggregates are mapped once, then the bidirectional JPA relation is linked.
            entity = mapper.toEntity(campaign);
            attachMappedItem(entity);
        } else {
            // Existing entities stay managed by the repository; update only their mapped state.
            mapper.updateEntity(campaign, entity);
            synchronizeItem(campaign, entity);
        }
        // Flush before returning so the optimistic version exposed as ETag is the committed value.
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    /** Aligns the persisted child with the aggregate's current one-item state. */
    private void synchronizeItem(Campaign campaign, CampaignJpaEntity entity) {
        if (campaign.item() == null) {
            // Removing the child lets orphanRemoval delete the old campaign_items row.
            entity.detachItem();
            return;
        }
        CampaignItemJpaEntity item = entity.getItem();
        if (item == null) {
            // A draft may receive its first item after the Campaign row already exists.
            entity.attachItem(mapper.toEntity(campaign.item()));
            return;
        }
        // Replace the child values without replacing its owning JPA identity.
        mapper.updateEntity(campaign.item(), item);
        entity.attachItem(item);
    }

    /** Restores the owning side of the Campaign/Item relation after MapStruct mapping. */
    private void attachMappedItem(CampaignJpaEntity entity) {
        if (entity.getItem() != null) {
            entity.attachItem(entity.getItem());
        }
    }
}
