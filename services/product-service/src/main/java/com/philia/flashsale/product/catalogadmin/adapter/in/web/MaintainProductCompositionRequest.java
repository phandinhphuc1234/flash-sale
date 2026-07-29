package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HTTP payload used to replace the editable catalog composition of one product. */
record MaintainProductCompositionRequest(
        @NotBlank String name,
        String shortDescription,
        String description,
        @Valid List<VariantRequest> variants,
        @Valid List<CategoryRequest> categories,
        @Valid List<MediaRequest> media) {

    MaintainProductCompositionRequest {
        variants = variants == null ? List.of() : List.copyOf(variants);
        categories = categories == null ? List.of() : List.copyOf(categories);
        media = media == null ? List.of() : List.copyOf(media);
    }

    record VariantRequest(
            UUID id,
            @NotBlank String sku,
            String barcode,
            @NotBlank String name,
            @NotNull BigDecimal basePrice,
            @NotBlank String currency,
            @NotNull VariantStatus status,
            int sortOrder) {
    }

    record CategoryRequest(@NotNull UUID id, boolean primary, int sortOrder) { }

    record MediaRequest(
            UUID id,
            UUID variantId,
            @NotBlank String mediaType,
            @NotBlank String url,
            String altText,
            int sortOrder,
            @NotBlank String status) { }
}
