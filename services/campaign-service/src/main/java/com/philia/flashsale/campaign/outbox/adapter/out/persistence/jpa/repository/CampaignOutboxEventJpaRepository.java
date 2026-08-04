package com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.entity.CampaignOutboxEventJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for durable lifecycle notification rows. */
public interface CampaignOutboxEventJpaRepository
        extends JpaRepository<CampaignOutboxEventJpaEntity, UUID> {

    List<CampaignOutboxEventJpaEntity> findByAggregateIdOrderByAggregateVersionAscIdAsc(
            UUID aggregateId);

    Optional<CampaignOutboxEventJpaEntity> findByIdAndAggregateId(UUID id, UUID aggregateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from CampaignOutboxEventJpaEntity event where event.id = :id")
    Optional<CampaignOutboxEventJpaEntity> findLockedById(@Param("id") UUID id);
}
