package com.philia.flashsale.product.catalog.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.catalog.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CatalogProductQuery;
import com.philia.flashsale.product.catalog.domain.CatalogPageRequest;
import com.philia.flashsale.product.catalog.domain.CatalogSort;
import com.philia.flashsale.product.catalog.domain.CategorySummary;
import com.philia.flashsale.product.catalog.domain.ProductDetail;
import com.philia.flashsale.product.catalog.domain.ProductSummary;

public class ProductCatalogQueryService implements BrowseCatalogUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_QUERY_CODE_POINTS = 100;

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
    public CatalogPage<ProductSummary> browseProducts(CatalogProductQuery query, int page, int size) {
        if (page < 0) {
            throw new InvalidCatalogRequestException("Page number must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCatalogRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        CatalogProductQuery normalized = normalize(query);
        if (normalized.minPrice() != null && normalized.minPrice().signum() < 0) {
            throw new InvalidCatalogRequestException("Minimum price must be greater than or equal to 0");
        }
        if (normalized.maxPrice() != null && normalized.maxPrice().signum() < 0) {
            throw new InvalidCatalogRequestException("Maximum price must be greater than or equal to 0");
        }
        if (normalized.minPrice() != null
                && normalized.maxPrice() != null
                && normalized.minPrice().compareTo(normalized.maxPrice()) > 0) {
            throw new InvalidCatalogRequestException("Minimum price must not exceed maximum price");
        }
        if (normalized.sort() == CatalogSort.RELEVANCE && normalized.text() == null) {
            throw new InvalidCatalogRequestException("Relevance sort requires a search query");
        }
        return loadCatalogPort.loadVisibleProducts(normalized, new CatalogPageRequest(page, size));
    }

    @Override
    // Public detail must go through the visible-product query, so drafts/inactive products cannot leak by slug.
    public ProductDetail viewProduct(String slug) {
        return loadCatalogPort.loadVisibleProduct(slug);
    }

    private static CatalogProductQuery normalize(CatalogProductQuery query) {
        CatalogProductQuery requested = query == null
                ? new CatalogProductQuery(null, null, null, null, CatalogSort.NEWEST)
                : query;
        String text = normalizeText(requested.text());
        if (text != null && text.codePointCount(0, text.length()) > MAX_QUERY_CODE_POINTS) {
            throw new InvalidCatalogRequestException("Search query must not exceed " + MAX_QUERY_CODE_POINTS + " characters");
        }
        return new CatalogProductQuery(
                text,
                normalizeText(requested.categorySlug()),
                requested.minPrice(),
                requested.maxPrice(),
                requested.sort() == null ? CatalogSort.NEWEST : requested.sort());
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
