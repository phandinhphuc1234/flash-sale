package com.philia.flashsale.product.catalogadmin.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.catalogadmin.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductSummaryResult;
import com.philia.flashsale.product.catalogadmin.domain.ProductAggregate;

public interface LoadAdminProductPort {

    AdminCatalogPageResult<AdminProductSummaryResult> browseAdminProducts(BrowseAdminCatalogQuery query);

    Optional<AdminProductDetailResult> loadAdminProduct(UUID productId);

    Optional<ProductAggregate> loadAdminAggregate(UUID productId);
}
