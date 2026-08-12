package com.philia.flashsale.product.catalog.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import com.philia.flashsale.common.web.PageMeta;
import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CategorySummary;
import com.philia.flashsale.product.catalog.domain.PageMetadata;
import com.philia.flashsale.product.catalog.domain.ProductCategorySummary;
import com.philia.flashsale.product.catalog.domain.ProductDetail;
import com.philia.flashsale.product.catalog.domain.ProductMediaSummary;
import com.philia.flashsale.product.catalog.domain.ProductSummary;
import com.philia.flashsale.product.catalog.domain.ProductVariantSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductCatalogWebMapper {

    CategoryResponse toResponse(CategorySummary category);

    ProductSummaryResponse toResponse(ProductSummary product);

    ProductDetailResponse toResponse(ProductDetail product);

    @Mapping(target = "basePrice", expression = "java(toPlainString(variant.basePrice()))")
    ProductVariantResponse toResponse(ProductVariantSummary variant);

    ProductCategoryResponse toResponse(ProductCategorySummary category);

    ProductMediaResponse toResponse(ProductMediaSummary media);

    PageMeta toResponse(PageMetadata page);

    // Page/list wrappers are adapter-specific HTTP shapes, so the mapper composes them at the web edge.
    default CatalogListResponse<CategoryResponse> toCategoryList(List<CategorySummary> categories) {
        return new CatalogListResponse<>(categories.stream()
                .map(this::toResponse)
                .toList());
    }

    default PageResponse<ProductSummaryResponse> toProductPage(CatalogPage<ProductSummary> products) {
        return new PageResponse<>(products.data().stream()
                .map(this::toResponse)
                .toList(), toResponse(products.page()));
    }

    default String toPlainString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
