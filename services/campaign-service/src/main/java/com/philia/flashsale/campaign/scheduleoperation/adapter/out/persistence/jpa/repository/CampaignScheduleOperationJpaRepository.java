package com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.entity.CampaignScheduleOperationJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

/** Spring Data boundary for durable schedule-operation identities and replay lookup. */
public interface CampaignScheduleOperationJpaRepository
        extends JpaRepository<CampaignScheduleOperationJpaEntity, UUID> {

    Optional<CampaignScheduleOperationJpaEntity> findByCampaignIdAndIdempotencyKey(
            UUID campaignId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CampaignScheduleOperationJpaEntity o "
            + "where o.campaignId = :campaignId and o.idempotencyKey = :idempotencyKey")
    Optional<CampaignScheduleOperationJpaEntity> findLockedByCampaignIdAndIdempotencyKey(
            @Param("campaignId") UUID campaignId,
            @Param("idempotencyKey") String idempotencyKey);

    Optional<CampaignScheduleOperationJpaEntity> findByInventoryRequestId(UUID inventoryRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CampaignScheduleOperationJpaEntity o where o.id = :id")
    Optional<CampaignScheduleOperationJpaEntity> findLockedById(@Param("id") UUID id);

    List<CampaignScheduleOperationJpaEntity> findByOperationStatusInOrderByUpdatedAtAsc(
            List<String> statuses, Pageable pageable);

    boolean existsByCampaignIdAndOperationStatusIn(UUID campaignId, List<String> statuses);
}
