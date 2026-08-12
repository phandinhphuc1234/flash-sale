package com.philia.flashsale.campaign.scheduleoperation.application.port.out;

import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;

/** Outbound boundary for persisting schedule-operation identity and state transitions. */
public interface SaveScheduleOperationPort {

    ScheduleOperation save(ScheduleOperation operation);
}
