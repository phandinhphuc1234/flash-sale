package com.philia.flashsale.product.catalogadmin.application.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.CatalogAdminActor;
import com.philia.flashsale.product.catalogadmin.domain.TraceId;
import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;

public record MaintainProductCompositionCommand(
        UUID productId,
        long expectedVersion,
        String name,
        String shortDescription,
        String description,
        List<VariantInput> variants,
        List<CategoryInput> categories,
        List<MediaInput> media,
        CatalogAdminActor actor,
        TraceId traceId) {

    public MaintainProductCompositionCommand {
        variants = variants == null ? List.of() : List.copyOf(variants);
        categories = categories == null ? List.of() : List.copyOf(categories);
        media = media == null ? List.of() : List.copyOf(media);
    }

    public record VariantInput(UUID id, String sku, String barcode, String name,
                               BigDecimal basePrice, String currency, VariantStatus status,
                               int sortOrder) { }

    public record CategoryInput(UUID id, boolean primary, int sortOrder) { }

    public record MediaInput(UUID id, UUID variantId, String mediaType, String url,
                             String altText, int sortOrder, String status) { }
}
