package com.philia.flashsale.product.application.port.out;

import com.philia.flashsale.product.domain.model.ProductAggregate;

public interface SaveAdminProductPort {

    ProductAggregate saveDraft(ProductAggregate product);
}
