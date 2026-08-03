package com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.entity.CampaignScheduleOperationJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Spring Data boundary for durable schedule-operation identities and replay lookup. */
public interface CampaignScheduleOperationJpaRepository
        extends JpaRepository<CampaignScheduleOperationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CampaignScheduleOperationJpaEntity> findByCampaignIdAndIdempotencyKey(
            UUID campaignId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CampaignScheduleOperationJpaEntity> findByInventoryRequestId(UUID inventoryRequestId);

    boolean existsByCampaignIdAndOperationStatusIn(UUID campaignId, List<String> statuses);
}
