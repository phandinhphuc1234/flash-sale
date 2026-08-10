package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
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

    @EntityGraph(attributePaths = "item")
    @Query("select c from CampaignJpaEntity c "
            + "where c.status = :status and c.startAt <= :now "
            + "and c.endAt > :now order by c.startAt asc, c.id asc")
    List<CampaignJpaEntity> findDueForActivation(
            @Param("status") CampaignStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @EntityGraph(attributePaths = "item")
    @Query("select c from CampaignJpaEntity c "
            + "where c.status = :status and c.endAt <= :now order by c.endAt asc, c.id asc")
    List<CampaignJpaEntity> findDueForEnding(
            @Param("status") CampaignStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CampaignJpaEntity c set c.status = com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus.ACTIVE, "
            + "c.activatedAt = :now, c.updatedBy = :actor, c.updatedAt = :now, c.version = c.version + 1 "
            + "where c.id = :id and c.status = :expectedStatus and c.version = :expectedVersion "
            + "and c.startAt <= :now and c.endAt > :now")
    int activateIfDue(
            @Param("id") UUID id,
            @Param("expectedStatus") CampaignStatus expectedStatus,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now,
            @Param("actor") String actor);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CampaignJpaEntity c set c.status = com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus.ENDED, "
            + "c.endedAt = :now, c.updatedBy = :actor, c.updatedAt = :now, c.version = c.version + 1 "
            + "where c.id = :id and c.status = :expectedStatus and c.version = :expectedVersion "
            + "and c.endAt <= :now")
    int endIfDue(
            @Param("id") UUID id,
            @Param("expectedStatus") CampaignStatus expectedStatus,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now,
            @Param("actor") String actor);
}
