package com.philia.flashsale.product.catalogadmin.domain;

public record CatalogAdminActor(String actorId) {

    public CatalogAdminActor {
        actorId = requireText(actorId, "Catalog admin actor is required");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
