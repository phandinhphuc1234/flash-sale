package com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.entity.CampaignOutboxEventJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for durable lifecycle notification rows. */
public interface CampaignOutboxEventJpaRepository
        extends JpaRepository<CampaignOutboxEventJpaEntity, UUID> {

    List<CampaignOutboxEventJpaEntity> findByAggregateIdOrderByAggregateVersionAscIdAsc(
            UUID aggregateId);
}
