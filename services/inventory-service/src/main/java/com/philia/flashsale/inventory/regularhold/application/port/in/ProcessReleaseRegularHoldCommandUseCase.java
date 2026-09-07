package com.philia.flashsale.inventory.regularhold.application.port.in;

import com.philia.flashsale.inventory.regularhold.application.model.ReleaseRegularHoldMessage;

public interface ProcessReleaseRegularHoldCommandUseCase {
    void process(ReleaseRegularHoldMessage message);
}
