package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductDraftRequest(
        @NotBlank(message = "Product code is required")
        @Size(max = 64, message = "Product code must not exceed 64 characters")
        String code,
        @NotBlank(message = "Product slug is required")
        @Size(max = 200, message = "Product slug must not exceed 200 characters")
        String slug,
        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,
        @Size(max = 500, message = "Product short description must not exceed 500 characters")
        String shortDescription,
        String description) {
}
