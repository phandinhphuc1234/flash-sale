package com.philia.flashsale.campaign.scheduleoperation.application.port.out;

import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import java.util.List;

/** Loads bounded STARTED/INVENTORY_ALLOCATED operations for background recovery. */
public interface LoadResumableScheduleOperationsPort {

    List<ScheduleOperation> findResumable(int limit);
}
