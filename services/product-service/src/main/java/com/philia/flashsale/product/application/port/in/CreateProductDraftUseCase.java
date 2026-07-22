package com.philia.flashsale.product.application.port.in;

import com.philia.flashsale.product.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;

public interface CreateProductDraftUseCase {

    CreateProductDraftResult createDraft(CreateProductDraftCommand command);
}
