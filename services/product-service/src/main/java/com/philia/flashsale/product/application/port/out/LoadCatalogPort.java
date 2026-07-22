package com.philia.flashsale.product.application.port.out;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.domain.model.CatalogPage;
import com.philia.flashsale.product.domain.model.CatalogPageRequest;
import com.philia.flashsale.product.domain.model.CategorySummary;
import com.philia.flashsale.product.domain.model.ProductDetail;
import com.philia.flashsale.product.domain.model.ProductSummary;

public interface LoadCatalogPort {

    List<CategorySummary> loadCategories(UUID parentId);

    CatalogPage<ProductSummary> loadVisibleProducts(String categorySlug, CatalogPageRequest pageRequest);

    ProductDetail loadVisibleProduct(String slug);
}
