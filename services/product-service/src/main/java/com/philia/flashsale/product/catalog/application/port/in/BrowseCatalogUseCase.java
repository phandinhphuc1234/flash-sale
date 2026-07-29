package com.philia.flashsale.product.catalog.application.port.in;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CategorySummary;
import com.philia.flashsale.product.catalog.domain.ProductDetail;
import com.philia.flashsale.product.catalog.domain.ProductSummary;

public interface BrowseCatalogUseCase {

    List<CategorySummary> browseCategories(UUID parentId);

    CatalogPage<ProductSummary> browseProducts(String categorySlug, int page, int size);

    ProductDetail viewProduct(String slug);
}
