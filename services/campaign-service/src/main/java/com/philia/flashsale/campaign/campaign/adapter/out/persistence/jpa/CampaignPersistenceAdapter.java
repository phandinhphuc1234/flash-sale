package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignItemJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.mapper.CampaignPersistenceMapper;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Persistence adapter for the Campaign aggregate.
 *
 * <p>Application output ports are introduced by the later use-case task; this
 * foundation adapter already keeps JPA entities and mapping at the outbound
 * boundary.</p>
 */
@Repository
public class CampaignPersistenceAdapter {

    private final CampaignJpaRepository repository;
    private final CampaignPersistenceMapper mapper;

    public CampaignPersistenceAdapter(
            CampaignJpaRepository repository,
            CampaignPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Loads the aggregate with its one-item MVP relation and maps it back to the domain model. */
    public Optional<Campaign> findById(UUID campaignId) {
        return repository.findDetailedById(campaignId).map(mapper::toDomain);
    }

    /** Checks code uniqueness through the Campaign-owned repository only. */
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    /**
     * Persists a new aggregate or updates the existing aggregate and its optional item.
     *
     * <p>The mapper keeps JPA types at this outbound boundary; the application layer
     * will provide the transaction and output-port contract in a later task.</p>
     */
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
        return mapper.toDomain(repository.save(entity));
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
