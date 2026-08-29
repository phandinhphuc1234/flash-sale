package com.philia.flashsale.product.catalog.application.port.out;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CatalogPageRequest;
import com.philia.flashsale.product.catalog.domain.CategorySummary;
import com.philia.flashsale.product.catalog.domain.ProductDetail;
import com.philia.flashsale.product.catalog.domain.ProductSummary;
import com.philia.flashsale.product.catalog.application.result.VariantDisplayResult;

public interface LoadCatalogPort {

    List<CategorySummary> loadCategories(UUID parentId);

    CatalogPage<ProductSummary> loadVisibleProducts(String categorySlug, CatalogPageRequest pageRequest);

    ProductDetail loadVisibleProduct(String slug);

    List<VariantDisplayResult> loadVariantDisplays(List<UUID> variantIds);
}
