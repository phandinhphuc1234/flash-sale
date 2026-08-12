package com.philia.flashsale.product.catalogadmin.application.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;
import com.philia.flashsale.product.catalogadmin.domain.CatalogAdminActor;
import com.philia.flashsale.product.catalogadmin.domain.TraceId;

public record CreateProductDraftCommand(
        CatalogAdminActor actor,
        TraceId traceId,
        String idempotencyKey,
        String code,
        String slug,
        String name,
        String shortDescription,
        String description) {

    public CreateProductDraftCommand {
        if (actor == null) {
            throw new IllegalArgumentException("Catalog admin actor is required");
        }
        if (traceId == null) {
            throw new IllegalArgumentException("Trace ID is required");
        }
        idempotencyKey = requireText(idempotencyKey, "Idempotency-Key is required");
        code = requireText(code, "Product code is required");
        slug = requireText(slug, "Product slug is required");
        name = requireText(name, "Product name is required");
        shortDescription = normalizeOptional(shortDescription);
        description = normalizeOptional(description);
    }

    public AdminCommandName commandName() {
        return AdminCommandName.CREATE_PRODUCT_DRAFT;
    }

    public String requestHash() {
        String canonical = commandName() + "\n"
                + code + "\n"
                + slug + "\n"
                + name + "\n"
                + (shortDescription == null ? "" : shortDescription) + "\n"
                + (description == null ? "" : description);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
