package com.philia.flashsale.product.catalogadmin.application.port.in;

import com.philia.flashsale.product.catalogadmin.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.catalogadmin.application.result.CreateProductDraftResult;

public interface CreateProductDraftUseCase {

    CreateProductDraftResult createDraft(CreateProductDraftCommand command);
}
