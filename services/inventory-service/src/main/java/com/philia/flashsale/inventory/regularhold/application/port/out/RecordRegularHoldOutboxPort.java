package com.philia.flashsale.inventory.regularhold.application.port.out;

import com.philia.flashsale.inventory.regularhold.application.model.RegularHoldFactOutboxEvent;

public interface RecordRegularHoldOutboxPort {
    void record(RegularHoldFactOutboxEvent event);
}
