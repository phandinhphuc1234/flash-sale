package com.philia.flashsale.inventory.configuration;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicitly opt-in inputs for the one-shot Inventory fixture Job. */
@ConfigurationProperties(prefix = "flashsale.inventory.fixture")
public class InventoryFixtureProperties {
    private boolean enabled;
    private UUID variantId;
    private String skuSnapshot;
    private long quantity;
    private String reason;

    public void validate() {
        if (!enabled) {
            return;
        }
        if (variantId == null) {
            throw new IllegalArgumentException("Inventory fixture variantId is required");
        }
        if (skuSnapshot == null || skuSnapshot.isBlank() || skuSnapshot.length() > 200) {
            throw new IllegalArgumentException("Inventory fixture skuSnapshot must be nonblank and bounded");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Inventory fixture quantity must be positive");
        }
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new IllegalArgumentException("Inventory fixture reason must be nonblank and bounded");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public void setSkuSnapshot(String skuSnapshot) {
        this.skuSnapshot = skuSnapshot;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
