package com.philia.flashsale.product.catalogadmin.application.port.in;

import com.philia.flashsale.product.catalogadmin.application.query.ViewAdminProductQuery;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;

public interface ViewAdminProductUseCase {

    AdminProductDetailResult view(ViewAdminProductQuery query);
}
