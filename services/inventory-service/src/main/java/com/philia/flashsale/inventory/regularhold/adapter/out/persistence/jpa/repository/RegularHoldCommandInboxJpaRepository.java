package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularHoldCommandInboxJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegularHoldCommandInboxJpaRepository extends JpaRepository<RegularHoldCommandInboxJpaEntity, UUID> {
    Optional<RegularHoldCommandInboxJpaEntity> findBySourceTopicAndSourcePartitionAndSourceOffset(
            String sourceTopic, int sourcePartition, long sourceOffset);
}
