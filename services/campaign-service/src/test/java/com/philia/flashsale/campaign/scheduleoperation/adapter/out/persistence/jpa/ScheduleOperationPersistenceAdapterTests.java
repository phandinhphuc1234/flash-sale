package com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.entity.CampaignScheduleOperationJpaEntity;
import com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.repository.CampaignScheduleOperationJpaRepository;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationFingerprint;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ScheduleOperationPersistenceAdapterTests {

    @Test
    void staleWorkerCannotDowngradeACompletedOperation() {
        CampaignScheduleOperationJpaRepository repository =
                Mockito.mock(CampaignScheduleOperationJpaRepository.class);
        ScheduleOperationPersistenceAdapter adapter = new ScheduleOperationPersistenceAdapter(repository);
        UUID operationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID inventoryRequestId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2030-08-01T10:00:00Z");
        CampaignScheduleOperationJpaEntity completed = entity(
                operationId, campaignId, inventoryRequestId, "COMPLETED", createdAt.plusSeconds(2));
        ScheduleOperation stale = ScheduleOperation.rehydrate(
                operationId,
                campaignId,
                "schedule-key",
                inventoryRequestId,
                new ScheduleOperationFingerprint("a".repeat(64)),
                1,
                ScheduleOperationStatus.INVENTORY_ALLOCATED,
                1,
                null,
                null,
                "admin",
                "campaign-service",
                "trace-1",
                createdAt,
                createdAt.plusSeconds(1));
        when(repository.findLockedById(operationId)).thenReturn(Optional.of(completed));

        ScheduleOperation result = adapter.save(stale);

        assertThat(result.status()).isEqualTo(ScheduleOperationStatus.COMPLETED);
        verify(repository, never()).saveAndFlush(completed);
    }

    private CampaignScheduleOperationJpaEntity entity(
            UUID operationId,
            UUID campaignId,
            UUID inventoryRequestId,
            String status,
            Instant updatedAt) {
        return new CampaignScheduleOperationJpaEntity(
                operationId,
                campaignId,
                "schedule-key",
                inventoryRequestId,
                "a".repeat(64),
                1,
                status,
                1,
                null,
                null,
                "admin",
                "campaign-service",
                "trace-1",
                updatedAt.minusSeconds(2),
                updatedAt);
    }
}
