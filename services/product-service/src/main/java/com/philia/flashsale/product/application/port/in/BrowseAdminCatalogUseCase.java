package com.philia.flashsale.product.application.port.in;

import com.philia.flashsale.product.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.application.result.AdminProductSummaryResult;

public interface BrowseAdminCatalogUseCase {

    AdminCatalogPageResult<AdminProductSummaryResult> browse(BrowseAdminCatalogQuery query);
}
