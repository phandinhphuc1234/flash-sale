package com.philia.flashsale.product.catalogadmin.application.port.in;

import com.philia.flashsale.product.catalogadmin.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.catalogadmin.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductSummaryResult;

public interface BrowseAdminCatalogUseCase {

    AdminCatalogPageResult<AdminProductSummaryResult> browse(BrowseAdminCatalogQuery query);
}
