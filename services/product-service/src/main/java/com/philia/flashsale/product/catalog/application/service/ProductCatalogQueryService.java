package com.philia.flashsale.product.catalog.application.service;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.catalog.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CatalogPageRequest;
import com.philia.flashsale.product.catalog.domain.CategorySummary;
import com.philia.flashsale.product.catalog.domain.ProductDetail;
import com.philia.flashsale.product.catalog.domain.ProductSummary;

public class ProductCatalogQueryService implements BrowseCatalogUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final LoadCatalogPort loadCatalogPort;

    public ProductCatalogQueryService(LoadCatalogPort loadCatalogPort) {
        this.loadCatalogPort = loadCatalogPort;
    }

    @Override
    // Category reads are delegated to the outbound port so JPA stays outside the application layer.
    public List<CategorySummary> browseCategories(UUID parentId) {
        return loadCatalogPort.loadCategories(parentId);
    }

    @Override
    // Validate paging at the use-case boundary before asking the persistence adapter for visible products.
    public CatalogPage<ProductSummary> browseProducts(String categorySlug, int page, int size) {
        if (page < 0) {
            throw new InvalidCatalogRequestException("Page number must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCatalogRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return loadCatalogPort.loadVisibleProducts(normalize(categorySlug), new CatalogPageRequest(page, size));
    }

    @Override
    // Public detail must go through the visible-product query, so drafts/inactive products cannot leak by slug.
    public ProductDetail viewProduct(String slug) {
        return loadCatalogPort.loadVisibleProduct(slug);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
