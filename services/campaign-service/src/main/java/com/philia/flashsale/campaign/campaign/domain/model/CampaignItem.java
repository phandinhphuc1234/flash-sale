package com.philia.flashsale.campaign.campaign.domain.model;

import java.util.UUID;

/**
 * The one Product variant configured for a Campaign.
 *
 * <p>Product and Inventory values remain nullable until scheduling obtains the
 * authoritative snapshots and allocation identity.</p>
 */
public final class CampaignItem {

    private final UUID id;
    private final UUID productId;
    private final UUID variantId;
    private final UUID inventoryAllocationId;
    private final String variantSkuSnapshot;
    private final CampaignMoney basePriceSnapshot;
    private final CampaignMoney campaignPrice;
    private final long requestedQuantity;
    private final long allocatedQuantity;
    private final long purchaseLimitPerUser;

    private CampaignItem(
            UUID id,
            UUID productId,
            UUID variantId,
            UUID inventoryAllocationId,
            String variantSkuSnapshot,
            CampaignMoney basePriceSnapshot,
            CampaignMoney campaignPrice,
            long requestedQuantity,
            long allocatedQuantity,
            long purchaseLimitPerUser) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.productId = productId;
        this.variantId = requireId(variantId, "Campaign variant id is required");
        this.inventoryAllocationId = inventoryAllocationId;
        this.variantSkuSnapshot = normalizeOptional(variantSkuSnapshot);
        this.basePriceSnapshot = basePriceSnapshot;
        this.campaignPrice = requireMoney(campaignPrice, "Campaign price is required");
        this.requestedQuantity = requirePositive(requestedQuantity, "Requested quantity must be positive");
        this.allocatedQuantity = requireNonNegative(allocatedQuantity, "Allocated quantity must not be negative");
        this.purchaseLimitPerUser = requirePositive(
                purchaseLimitPerUser, "Purchase limit per user must be positive");
        if (purchaseLimitPerUser > requestedQuantity) {
            throw new IllegalArgumentException("Purchase limit per user cannot exceed requested quantity");
        }
        if (basePriceSnapshot != null && !campaignPrice.isLessThan(basePriceSnapshot)) {
            throw new IllegalArgumentException("Campaign price must be lower than the base price");
        }
        if (allocatedQuantity > requestedQuantity) {
            throw new IllegalArgumentException("Allocated quantity cannot exceed requested quantity");
        }
    }

    /** Creates the editable item before Product and Inventory snapshots exist. */
    public static CampaignItem createDraft(
            UUID id,
            UUID variantId,
            CampaignMoney campaignPrice,
            long requestedQuantity,
            long purchaseLimitPerUser) {
        return new CampaignItem(
                id,
                null,
                variantId,
                null,
                null,
                null,
                campaignPrice,
                requestedQuantity,
                0,
                purchaseLimitPerUser);
    }

    /** Rehydrates a Campaign item from persistence without invoking a use-case workflow. */
    public static CampaignItem rehydrate(
            UUID id,
            UUID productId,
            UUID variantId,
            UUID inventoryAllocationId,
            String variantSkuSnapshot,
            CampaignMoney basePriceSnapshot,
            CampaignMoney campaignPrice,
            long requestedQuantity,
            long allocatedQuantity,
            long purchaseLimitPerUser) {
        return new CampaignItem(
                id,
                productId,
                variantId,
                inventoryAllocationId,
                variantSkuSnapshot,
                basePriceSnapshot,
                campaignPrice,
                requestedQuantity,
                allocatedQuantity,
                purchaseLimitPerUser);
    }

    /** Returns a copy with the authoritative Product snapshot captured. */
    public CampaignItem withProductSnapshot(UUID productId, String sku, CampaignMoney basePrice) {
        return new CampaignItem(
                id,
                requireId(productId, "Product id is required for a snapshot"),
                variantId,
                inventoryAllocationId,
                requireText(sku, "Variant SKU snapshot is required"),
                requireMoney(basePrice, "Base price snapshot is required"),
                campaignPrice,
                requestedQuantity,
                allocatedQuantity,
                purchaseLimitPerUser);
    }

    /** Returns a copy with the complete Inventory allocation identity and quantity. */
    public CampaignItem withInventoryAllocation(UUID allocationId, long allocatedQuantity) {
        if (allocatedQuantity != requestedQuantity) {
            throw new IllegalArgumentException("Inventory must allocate the full requested quantity");
        }
        return new CampaignItem(
                id,
                productId,
                variantId,
                requireId(allocationId, "Inventory allocation id is required"),
                variantSkuSnapshot,
                basePriceSnapshot,
                campaignPrice,
                requestedQuantity,
                allocatedQuantity,
                purchaseLimitPerUser);
    }

    public boolean hasCompleteProductSnapshot() {
        return productId != null && variantSkuSnapshot != null && basePriceSnapshot != null;
    }

    public boolean hasCompleteAllocation() {
        return inventoryAllocationId != null && allocatedQuantity == requestedQuantity;
    }

    public boolean isReadyForScheduling() {
        return hasCompleteProductSnapshot() && hasCompleteAllocation();
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public UUID variantId() {
        return variantId;
    }

    public UUID inventoryAllocationId() {
        return inventoryAllocationId;
    }

    public String variantSkuSnapshot() {
        return variantSkuSnapshot;
    }

    public CampaignMoney basePriceSnapshot() {
        return basePriceSnapshot;
    }

    public CampaignMoney campaignPrice() {
        return campaignPrice;
    }

    public long requestedQuantity() {
        return requestedQuantity;
    }

    public long allocatedQuantity() {
        return allocatedQuantity;
    }

    public long purchaseLimitPerUser() {
        return purchaseLimitPerUser;
    }

    private static UUID requireId(UUID value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static CampaignMoney requireMoney(CampaignMoney value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static long requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static long requireNonNegative(long value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
