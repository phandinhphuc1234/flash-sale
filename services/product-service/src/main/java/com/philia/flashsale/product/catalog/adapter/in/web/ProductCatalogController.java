package com.philia.flashsale.product.catalog.adapter.in.web;

import java.util.UUID;

import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.product.catalog.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.catalog.application.service.ProductCatalogQueryService;
import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.ProductSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
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
    ApiResponse<CatalogListResponse<CategoryResponse>> browseCategories(
            @RequestParam(required = false) UUID parentId) {
        return ApiResponse.success(mapper.toCategoryList(browseCatalogUseCase.browseCategories(parentId)));
    }

    // Shopper listing only returns products that satisfy the public visibility rules from the query use case.
    @GetMapping("/products")
    ApiResponse<PageResponse<ProductSummaryResponse>> browseProducts(
            @RequestParam(required = false) String categorySlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ProductCatalogQueryService.DEFAULT_PAGE_SIZE) int size) {
        CatalogPage<ProductSummary> products =
                browseCatalogUseCase.browseProducts(categorySlug, page, size);
        return ApiResponse.success(mapper.toProductPage(products));
    }

    // Detail lookup is slug-based because this is the client-facing, SEO-friendly catalog contract.
    @GetMapping("/products/{slug}")
    ApiResponse<ProductDetailResponse> viewProduct(@PathVariable String slug) {
        return ApiResponse.success(mapper.toResponse(browseCatalogUseCase.viewProduct(slug)));
    }
}
