package com.philia.flashsale.campaign.scheduleoperation.application.port.out;

import java.time.Instant;

/** Provides UTC timestamps without coupling schedule-operation application logic to a clock API. */
@FunctionalInterface
public interface ScheduleOperationClockPort {

    Instant now();
}
