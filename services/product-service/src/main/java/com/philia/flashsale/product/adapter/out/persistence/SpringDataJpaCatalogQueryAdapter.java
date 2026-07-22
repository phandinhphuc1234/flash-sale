package com.philia.flashsale.product.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.philia.flashsale.product.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.application.service.CategoryNotFoundException;
import com.philia.flashsale.product.application.service.ProductNotFoundException;
import com.philia.flashsale.product.domain.model.CatalogPage;
import com.philia.flashsale.product.domain.model.CatalogPageRequest;
import com.philia.flashsale.product.domain.model.CategorySummary;
import com.philia.flashsale.product.domain.model.PageMetadata;
import com.philia.flashsale.product.domain.model.ProductCategorySummary;
import com.philia.flashsale.product.domain.model.ProductDetail;
import com.philia.flashsale.product.domain.model.ProductMediaSummary;
import com.philia.flashsale.product.domain.model.ProductSummary;
import com.philia.flashsale.product.domain.model.ProductVariantSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class SpringDataJpaCatalogQueryAdapter implements LoadCatalogPort {

    private static final String ACTIVE = "ACTIVE";

    private final ProductReadJpaRepository productRepository;
    private final CategoryReadJpaRepository categoryRepository;

    SpringDataJpaCatalogQueryAdapter(
            ProductReadJpaRepository productRepository,
            CategoryReadJpaRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    // Read categories from JPA and return domain-facing summaries instead of exposing entities outward.
    public List<CategorySummary> loadCategories(UUID parentId) {
        List<CategoryJpaEntity> categories = parentId == null
                ? categoryRepository.findByParentIdIsNullAndStatusOrderBySortOrderAscIdAsc(ACTIVE)
                : categoryRepository.findByParentIdAndStatusOrderBySortOrderAscIdAsc(parentId, ACTIVE);
        return categories.stream()
                .map(category -> new CategorySummary(
                        category.getId(),
                        category.getParentId(),
                        category.getSlug(),
                        category.getName(),
                        category.getSortOrder()))
                .toList();
    }

    @Override
    // Fetch product rows first, then batch-load variants to avoid an N+1 query pattern on catalog pages.
    public CatalogPage<ProductSummary> loadVisibleProducts(
            String categorySlug,
            CatalogPageRequest pageRequest) {
        if (categorySlug != null && !categoryRepository.existsBySlug(categorySlug)) {
            throw new CategoryNotFoundException(categorySlug);
        }

        PageRequest pageable = PageRequest.of(pageRequest.page(), pageRequest.size());
        Page<ProductReadJpaRepository.ProductRow> page = categorySlug == null
                ? productRepository.findVisibleProducts(pageable)
                : productRepository.findVisibleProductsByCategorySlug(categorySlug, pageable);

        List<UUID> productIds = page.getContent().stream()
                .map(ProductReadJpaRepository.ProductRow::getId)
                .toList();
        Map<UUID, List<ProductVariantSummary>> variants = variantsByProductId(productIds);

        List<ProductSummary> products = page.getContent().stream()
                .map(product -> new ProductSummary(
                        product.getId(),
                        product.getCode(),
                        product.getSlug(),
                        product.getName(),
                        product.getShortDescription(),
                        variants.getOrDefault(product.getId(), List.of())))
                .toList();

        return new CatalogPage<>(
                products,
                new PageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.hasNext()));
    }

    @Override
    // Detail view composes one public read model from product, variants, categories, and media tables.
    public ProductDetail loadVisibleProduct(String slug) {
        ProductReadJpaRepository.ProductRow product = productRepository.findVisibleProductBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException(slug));
        List<UUID> productIds = List.of(product.getId());

        return new ProductDetail(
                product.getId(),
                product.getCode(),
                product.getSlug(),
                product.getName(),
                product.getShortDescription(),
                product.getDescription(),
                variantsByProductId(productIds).getOrDefault(product.getId(), List.of()),
                categoriesByProductId(productIds).getOrDefault(product.getId(), List.of()),
                mediaByProductId(productIds).getOrDefault(product.getId(), List.of()));
    }

    // Batch helpers preserve ordering from repository queries while grouping rows by their owning product id.
    private Map<UUID, List<ProductVariantSummary>> variantsByProductId(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findActiveVariantsByProductIds(productIds).stream()
                .collect(groupingByProductId(
                        ProductReadJpaRepository.VariantRow::getProductId,
                        row -> new ProductVariantSummary(
                                row.getId(),
                                row.getSku(),
                                row.getName(),
                                row.getBasePrice(),
                                row.getCurrency())));
    }

    private Map<UUID, List<ProductCategorySummary>> categoriesByProductId(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findCategoriesByProductIds(productIds).stream()
                .collect(groupingByProductId(
                        ProductReadJpaRepository.ProductCategoryRow::getProductId,
                        row -> new ProductCategorySummary(
                                row.getId(),
                                row.getSlug(),
                                row.getName(),
                                row.isPrimaryCategory(),
                                row.getSortOrder())));
    }

    private Map<UUID, List<ProductMediaSummary>> mediaByProductId(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findActiveMediaByProductIds(productIds).stream()
                .collect(groupingByProductId(
                        ProductReadJpaRepository.ProductMediaRow::getProductId,
                        row -> new ProductMediaSummary(
                                row.getId(),
                                row.getMediaType(),
                                row.getUrl(),
                                row.getAltText(),
                                row.getSortOrder())));
    }

    private static <T, R> java.util.stream.Collector<T, ?, Map<UUID, List<R>>> groupingByProductId(
            Function<T, UUID> productId,
            Function<T, R> mapper) {
        return Collectors.groupingBy(
                productId,
                LinkedHashMap::new,
                Collectors.mapping(mapper, Collectors.toCollection(ArrayList::new)));
    }
}
