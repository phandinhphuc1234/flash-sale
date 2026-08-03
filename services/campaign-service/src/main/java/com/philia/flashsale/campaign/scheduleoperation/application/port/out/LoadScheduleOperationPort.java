package com.philia.flashsale.campaign.scheduleoperation.application.port.out;

import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import java.util.Optional;
import java.util.UUID;

/** Outbound boundary for replay and recovery lookups of retained schedule operations. */
public interface LoadScheduleOperationPort {

    Optional<ScheduleOperation> findByCampaignIdAndIdempotencyKey(UUID campaignId, String idempotencyKey);

    Optional<ScheduleOperation> findByInventoryRequestId(UUID inventoryRequestId);

    boolean existsInFlightByCampaignId(UUID campaignId);
}
