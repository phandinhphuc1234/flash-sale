package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data boundary for Campaign-owned persistence. */
public interface CampaignJpaRepository extends JpaRepository<CampaignJpaEntity, UUID> {

    @EntityGraph(attributePaths = "item")
    Optional<CampaignJpaEntity> findDetailedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "item")
    @Query("select c from CampaignJpaEntity c where c.id = :id")
    Optional<CampaignJpaEntity> findDetailedByIdForUpdate(@Param("id") UUID id);

    boolean existsByCodeIgnoreCase(String code);

    Optional<CampaignJpaEntity> findByCode(String code);
}
