package com.philia.flashsale.product.catalogadmin.application.port.in;

import com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand;
import com.philia.flashsale.product.catalogadmin.application.result.MaintainProductCompositionResult;

public interface MaintainProductCompositionUseCase {
    MaintainProductCompositionResult maintain(MaintainProductCompositionCommand command);
}
