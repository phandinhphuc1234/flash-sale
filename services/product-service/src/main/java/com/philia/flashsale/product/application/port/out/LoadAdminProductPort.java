package com.philia.flashsale.product.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.product.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.application.result.AdminProductSummaryResult;

public interface LoadAdminProductPort {

    AdminCatalogPageResult<AdminProductSummaryResult> browseAdminProducts(BrowseAdminCatalogQuery query);

    Optional<AdminProductDetailResult> loadAdminProduct(UUID productId);
}
