package com.philia.flashsale.product.catalogadmin.application.exception;

import java.util.UUID;

public final class StaleProductVersionException extends RuntimeException {
    private final UUID productId;
    private final long currentVersion;

    public StaleProductVersionException(UUID productId, long currentVersion) {
        super("Product version is stale");
        this.productId = productId;
        this.currentVersion = currentVersion;
    }

    public UUID productId() { return productId; }
    public long currentVersion() { return currentVersion; }
}
