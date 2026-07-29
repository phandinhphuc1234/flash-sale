package com.philia.flashsale.product.catalogadmin.application.port.in;

import com.philia.flashsale.product.catalogadmin.application.command.ChangeProductLifecycleCommand;
import com.philia.flashsale.product.catalogadmin.application.result.ProductLifecycleResult;

public interface PublishProductUseCase { ProductLifecycleResult publish(ChangeProductLifecycleCommand command); }
