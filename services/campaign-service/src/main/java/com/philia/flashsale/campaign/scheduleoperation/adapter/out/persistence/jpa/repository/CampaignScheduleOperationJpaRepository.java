package com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.entity.CampaignScheduleOperationJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for durable schedule-operation identities and replay lookup. */
public interface CampaignScheduleOperationJpaRepository
        extends JpaRepository<CampaignScheduleOperationJpaEntity, UUID> {

    Optional<CampaignScheduleOperationJpaEntity> findByCampaignIdAndIdempotencyKey(
            UUID campaignId, String idempotencyKey);

    Optional<CampaignScheduleOperationJpaEntity> findByInventoryRequestId(UUID inventoryRequestId);
}
