package com.philia.flashsale.product.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import com.philia.flashsale.product.domain.model.CatalogPage;
import com.philia.flashsale.product.domain.model.CategorySummary;
import com.philia.flashsale.product.domain.model.PageMetadata;
import com.philia.flashsale.product.domain.model.ProductCategorySummary;
import com.philia.flashsale.product.domain.model.ProductDetail;
import com.philia.flashsale.product.domain.model.ProductMediaSummary;
import com.philia.flashsale.product.domain.model.ProductSummary;
import com.philia.flashsale.product.domain.model.ProductVariantSummary;
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

    PageResponse toResponse(PageMetadata page);

    // Page/list wrappers are adapter-specific HTTP shapes, so the mapper composes them at the web edge.
    default CatalogListResponse<CategoryResponse> toCategoryList(List<CategorySummary> categories) {
        return new CatalogListResponse<>(categories.stream()
                .map(this::toResponse)
                .toList());
    }

    default CatalogPageResponse<ProductSummaryResponse> toProductPage(CatalogPage<ProductSummary> products) {
        return new CatalogPageResponse<>(products.data().stream()
                .map(this::toResponse)
                .toList(), toResponse(products.page()));
    }

    default String toPlainString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
