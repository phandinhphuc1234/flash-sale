package com.philia.flashsale.campaign.scheduleoperation.application.port.in;

import com.philia.flashsale.campaign.scheduleoperation.application.command.PrepareScheduleOperationCommand;
import com.philia.flashsale.campaign.scheduleoperation.application.result.ScheduleOperationPreparationResult;

/** Input boundary for the short, local schedule-operation preparation transaction. */
public interface PrepareScheduleOperationUseCase {

    ScheduleOperationPreparationResult prepare(PrepareScheduleOperationCommand command);
}
