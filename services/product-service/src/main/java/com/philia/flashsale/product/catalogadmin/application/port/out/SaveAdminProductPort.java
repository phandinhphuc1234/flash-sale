package com.philia.flashsale.product.catalogadmin.application.port.out;

import com.philia.flashsale.product.catalogadmin.domain.ProductAggregate;

public interface SaveAdminProductPort {

    ProductAggregate saveDraft(ProductAggregate product);
}
