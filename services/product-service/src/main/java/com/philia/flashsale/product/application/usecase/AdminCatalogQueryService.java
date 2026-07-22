package com.philia.flashsale.product.application.usecase;

import com.philia.flashsale.product.application.exception.AdminProductNotFoundException;
import com.philia.flashsale.product.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.application.query.ViewAdminProductQuery;
import com.philia.flashsale.product.application.port.in.BrowseAdminCatalogUseCase;
import com.philia.flashsale.product.application.port.in.ViewAdminProductUseCase;
import com.philia.flashsale.product.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.application.result.AdminProductSummaryResult;

public final class AdminCatalogQueryService
        implements BrowseAdminCatalogUseCase, ViewAdminProductUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final LoadAdminProductPort loadAdminProductPort;

    public AdminCatalogQueryService(LoadAdminProductPort loadAdminProductPort) {
        this.loadAdminProductPort = loadAdminProductPort;
    }

    @Override
    // Admin reads use relaxed visibility compared with shopper catalog reads so operators can inspect drafts.
    public AdminCatalogPageResult<AdminProductSummaryResult> browse(BrowseAdminCatalogQuery query) {
        if (query.page() < 0) {
            throw new IllegalArgumentException("Page number must be greater than or equal to 0");
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return loadAdminProductPort.browseAdminProducts(query);
    }

    @Override
    // The use case turns a missing persistence row into an application-level not-found error.
    public AdminProductDetailResult view(ViewAdminProductQuery query) {
        return loadAdminProductPort.loadAdminProduct(query.productId())
                .orElseThrow(() -> new AdminProductNotFoundException(query.productId()));
    }
}
