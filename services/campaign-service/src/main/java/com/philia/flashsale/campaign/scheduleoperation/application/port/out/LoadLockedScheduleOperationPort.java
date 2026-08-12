package com.philia.flashsale.campaign.scheduleoperation.application.port.out;

import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import java.util.Optional;
import java.util.UUID;

/** Loads an operation under the database lock owned by the preparation transaction. */
public interface LoadLockedScheduleOperationPort extends LoadScheduleOperationPort {

    Optional<ScheduleOperation> findLockedByCampaignIdAndIdempotencyKey(
            UUID campaignId, String idempotencyKey);

    Optional<ScheduleOperation> findLockedById(UUID operationId);
}
