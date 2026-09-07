package com.philia.flashsale.inventory.regularhold.application.port.in;

import com.philia.flashsale.inventory.regularhold.application.model.ConfirmRegularHoldMessage;

public interface ProcessConfirmRegularHoldCommandUseCase {
    void process(ConfirmRegularHoldMessage message);
}
