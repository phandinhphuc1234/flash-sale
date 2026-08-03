package com.philia.flashsale.campaign.scheduleoperation.application.result;

import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import java.util.Objects;

/** Result returned to the schedule orchestrator after the short preparation transaction. */
public record ScheduleOperationPreparationResult(
        ScheduleOperation operation,
        boolean replayed) {

    public ScheduleOperationPreparationResult {
        Objects.requireNonNull(operation, "Prepared schedule operation is required");
    }

    public boolean allocationAlreadyConfirmed() {
        return operation.status() == ScheduleOperationStatus.INVENTORY_ALLOCATED
                || operation.status() == ScheduleOperationStatus.COMPLETED;
    }
}
