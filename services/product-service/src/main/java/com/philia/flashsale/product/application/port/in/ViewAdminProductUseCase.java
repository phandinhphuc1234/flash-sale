package com.philia.flashsale.product.application.port.in;

import com.philia.flashsale.product.application.query.ViewAdminProductQuery;
import com.philia.flashsale.product.application.result.AdminProductDetailResult;

public interface ViewAdminProductUseCase {

    AdminProductDetailResult view(ViewAdminProductQuery query);
}
