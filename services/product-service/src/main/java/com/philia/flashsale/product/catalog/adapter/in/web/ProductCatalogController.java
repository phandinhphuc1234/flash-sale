package com.philia.flashsale.product.catalog.adapter.in.web;

import java.math.BigDecimal;
import java.util.UUID;

import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.product.catalog.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.catalog.application.service.ProductCatalogQueryService;
import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CatalogProductQuery;
import com.philia.flashsale.product.catalog.domain.CatalogSort;
import com.philia.flashsale.product.catalog.domain.ProductSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Product catalog", description = "Public, read-only catalog browsing")
class ProductCatalogController {

    private final BrowseCatalogUseCase browseCatalogUseCase;
    private final ProductCatalogWebMapper mapper;

    ProductCatalogController(
            BrowseCatalogUseCase browseCatalogUseCase,
            ProductCatalogWebMapper mapper) {
        this.browseCatalogUseCase = browseCatalogUseCase;
        this.mapper = mapper;
    }

    // Public category browsing is read-only and safe to expose through the gateway without authentication.
    @GetMapping("/categories")
    @Operation(summary = "Browse product categories", description = "Optionally filters categories by parent category ID.")
    ApiResponse<CatalogListResponse<CategoryResponse>> browseCategories(
            @RequestParam(required = false) UUID parentId) {
        return ApiResponse.success(mapper.toCategoryList(browseCatalogUseCase.browseCategories(parentId)));
    }

    // Shopper listing only returns products that satisfy the public visibility rules from the query use case.
    @GetMapping("/products")
    @Operation(summary = "Browse published products", description = "Returns a paginated public catalog with optional search, category, price, and sort filters.")
    ApiResponse<PageResponse<ProductSummaryResponse>> browseProducts(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "NEWEST") CatalogSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ProductCatalogQueryService.DEFAULT_PAGE_SIZE) int size) {
        CatalogPage<ProductSummary> products =
                browseCatalogUseCase.browseProducts(
                        new CatalogProductQuery(query, categorySlug, minPrice, maxPrice, sort),
                        page,
                        size);
        return ApiResponse.success(mapper.toProductPage(products));
    }

    // Detail lookup is slug-based because this is the client-facing, SEO-friendly catalog contract.
    @GetMapping("/products/{slug}")
    @Operation(summary = "View product detail", description = "Returns a published product using its client-facing SEO-friendly slug.")
    ApiResponse<ProductDetailResponse> viewProduct(@PathVariable String slug) {
        return ApiResponse.success(mapper.toResponse(browseCatalogUseCase.viewProduct(slug)));
    }
}
