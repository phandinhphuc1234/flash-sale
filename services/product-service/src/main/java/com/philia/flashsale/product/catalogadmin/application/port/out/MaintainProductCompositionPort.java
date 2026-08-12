package com.philia.flashsale.product.catalogadmin.application.port.out;

import com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand;
import com.philia.flashsale.product.catalogadmin.application.result.MaintainProductCompositionResult;

public interface MaintainProductCompositionPort {
    MaintainProductCompositionResult replaceComposition(MaintainProductCompositionCommand command);
}
