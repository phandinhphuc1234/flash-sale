package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for Campaign-owned persistence. */
public interface CampaignJpaRepository extends JpaRepository<CampaignJpaEntity, UUID> {

    @EntityGraph(attributePaths = "item")
    Optional<CampaignJpaEntity> findDetailedById(UUID id);

    boolean existsByCodeIgnoreCase(String code);

    Optional<CampaignJpaEntity> findByCode(String code);
}
