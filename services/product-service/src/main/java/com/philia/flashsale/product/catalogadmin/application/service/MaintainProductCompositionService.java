package com.philia.flashsale.product.catalogadmin.application.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand;
import com.philia.flashsale.product.catalogadmin.application.port.in.MaintainProductCompositionUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadExistingCategoriesPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.MaintainProductCompositionPort;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductVariantResult;
import com.philia.flashsale.product.catalogadmin.application.result.MaintainProductCompositionResult;
import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;
import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;
import com.philia.flashsale.product.catalogadmin.domain.exception.ProductOwnershipMismatchException;
import com.philia.flashsale.product.catalogadmin.domain.exception.InvalidMoneyException;
import com.philia.flashsale.product.catalogadmin.domain.exception.ImmutablePublishedIdentifierException;
import com.philia.flashsale.product.catalogadmin.application.exception.AdminProductNotFoundException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateVariantBarcodeException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateVariantSkuException;
import com.philia.flashsale.product.catalogadmin.application.exception.StaleProductVersionException;

public final class MaintainProductCompositionService implements MaintainProductCompositionUseCase {
    private final LoadAdminProductPort loader;
    private final CheckCatalogUniquenessPort uniqueness;
    private final LoadExistingCategoriesPort categories;
    private final MaintainProductCompositionPort persistence;

    public MaintainProductCompositionService(LoadAdminProductPort loader,
            CheckCatalogUniquenessPort uniqueness, LoadExistingCategoriesPort categories,
            MaintainProductCompositionPort persistence) {
        this.loader = loader;
        this.uniqueness = uniqueness;
        this.categories = categories;
        this.persistence = persistence;
    }

    @Override
    public MaintainProductCompositionResult maintain(MaintainProductCompositionCommand command) {
        AdminProductDetailResult current = loader.loadAdminProduct(command.productId())
                .orElseThrow(() -> new AdminProductNotFoundException(command.productId()));
        if (current.version() != command.expectedVersion()) {
            throw new StaleProductVersionException(command.productId(), current.version());
        }
        if (current.status() == ProductStatus.ARCHIVED) {
            throw new com.philia.flashsale.product.catalogadmin.domain.exception.ArchivedProductImmutableException(
                    "Archived Product cannot be mutated");
        }
        validate(command, current);
        return persistence.replaceComposition(command);
    }

    private void validate(MaintainProductCompositionCommand command, AdminProductDetailResult current) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        Set<UUID> variantIds = new HashSet<>();
        Set<String> skus = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        Set<UUID> existingIds = current.variants().stream().map(AdminProductVariantResult::id).collect(java.util.stream.Collectors.toSet());
        for (MaintainProductCompositionCommand.VariantInput variant : command.variants()) {
            if (variant.id() != null && !existingIds.contains(variant.id())) {
                throw new ProductOwnershipMismatchException("Variant does not belong to Product");
            }
            UUID variantIdentity = variant.id() == null ? UUID.randomUUID() : variant.id();
            if (!variantIds.add(variantIdentity)) {
                throw new IllegalArgumentException("Variant must not be repeated");
            }
            if (variant.name() == null || variant.name().isBlank()) {
                throw new IllegalArgumentException("Variant name is required");
            }
            String normalizedSku = variant.sku() == null ? null : variant.sku().trim().toLowerCase();
            if (normalizedSku == null || normalizedSku.isBlank() || !skus.add(normalizedSku)) {
                throw new DuplicateVariantSkuException(variant.sku());
            }
            if (variant.id() == null && uniqueness.variantSkuExists(variant.sku().trim())) {
                throw new DuplicateVariantSkuException(variant.sku());
            }
            if (variant.id() != null) {
                current.variants().stream()
                        .filter(existing -> existing.id().equals(variant.id()))
                        .findFirst()
                        .ifPresent(existing -> {
                            if (current.variants().stream().anyMatch(other -> !other.id().equals(variant.id())
                                    && other.sku().equalsIgnoreCase(variant.sku().trim()))) {
                                throw new DuplicateVariantSkuException(variant.sku());
                            }
                            if (current.status() == ProductStatus.ACTIVE
                                    && !existing.sku().equals(variant.sku().trim())) {
                                throw new ImmutablePublishedIdentifierException(
                                        "Variant SKU cannot change while Product is ACTIVE");
                            }
                        });
            }
            if (variant.barcode() != null && !variant.barcode().isBlank()) {
                if (!barcodes.add(variant.barcode().trim()) || (variant.id() == null && uniqueness.barcodeExists(variant.barcode().trim()))) {
                    throw new DuplicateVariantBarcodeException(variant.barcode());
                }
            }
            if (variant.basePrice() == null || variant.basePrice().compareTo(BigDecimal.ZERO) < 0
                    || variant.basePrice().scale() > 4 || !"VND".equals(variant.currency())) {
                throw new InvalidMoneyException("Variant base price must be non-negative VND with at most 4 decimals");
            }
            if (variant.status() == null || variant.status() == VariantStatus.ARCHIVED && current.status() != ProductStatus.ARCHIVED) {
                throw new IllegalArgumentException("Variant status is invalid");
            }
            if (variant.sortOrder() < 0) throw new IllegalArgumentException("Variant sort order must be non-negative");
        }
        long primaryCount = command.categories().stream().filter(MaintainProductCompositionCommand.CategoryInput::primary).count();
        if (primaryCount > 1) throw new IllegalArgumentException("Product may have at most one primary Category");
        Set<UUID> categoryIds = command.categories().stream().map(MaintainProductCompositionCommand.CategoryInput::id).collect(java.util.stream.Collectors.toSet());
        if (categories.existingCategoryIds(categoryIds).size() != categoryIds.size()) {
            throw new com.philia.flashsale.product.catalogadmin.application.exception.CategoryNotFoundException();
        }
        for (MaintainProductCompositionCommand.CategoryInput category : command.categories()) {
            if (category.sortOrder() < 0) {
                throw new IllegalArgumentException("Category sort order must be non-negative");
            }
        }
        for (MaintainProductCompositionCommand.MediaInput media : command.media()) {
            if (media.variantId() != null && !existingIds.contains(media.variantId())
                    && command.variants().stream().noneMatch(v -> media.variantId().equals(v.id()))) {
                throw new ProductOwnershipMismatchException("Media Variant does not belong to Product");
            }
            if (media.url() == null || media.url().isBlank() || media.mediaType() == null
                    || !(media.mediaType().equals("IMAGE") || media.mediaType().equals("VIDEO"))) {
                throw new IllegalArgumentException("Media type and URL are required");
            }
            if (media.sortOrder() < 0) throw new IllegalArgumentException("Media sort order must be non-negative");
            if (!("ACTIVE".equals(media.status()) || "INACTIVE".equals(media.status())
                    || "ARCHIVED".equals(media.status()))) {
                throw new IllegalArgumentException("Media status is invalid");
            }
        }
    }
}
