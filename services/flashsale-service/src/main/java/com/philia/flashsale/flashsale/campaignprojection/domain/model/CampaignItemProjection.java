package com.philia.flashsale.flashsale.campaignprojection.domain.model;

import com.philia.flashsale.flashsale.campaignprojection.domain.exception.InvalidCampaignProjectionException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** Immutable item snapshot required by the Flash Sale admission path. */
public record CampaignItemProjection(
        UUID variantId,
        UUID inventoryAllocationId,
        String skuSnapshot,
        BigDecimal saleUnitPrice,
        String currency,
        long allocatedQuantity,
        long perUserLimit) {

    public CampaignItemProjection {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(inventoryAllocationId, "inventoryAllocationId");
        Objects.requireNonNull(skuSnapshot, "skuSnapshot");
        Objects.requireNonNull(saleUnitPrice, "saleUnitPrice");
        Objects.requireNonNull(currency, "currency");
        if (skuSnapshot.isBlank() || skuSnapshot.length() > 120) {
            throw new InvalidCampaignProjectionException("SKU snapshot must contain 1..120 characters");
        }
        try {
            saleUnitPrice = saleUnitPrice.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidCampaignProjectionException("Sale price must use at most four decimals");
        }
        if (saleUnitPrice.signum() < 0) {
            throw new InvalidCampaignProjectionException("Sale price cannot be negative");
        }
        if (!currency.matches("[A-Z]{3}")) {
            throw new InvalidCampaignProjectionException("Currency must be three uppercase letters");
        }
        if (allocatedQuantity <= 0 || perUserLimit <= 0) {
            throw new InvalidCampaignProjectionException("Allocation and per-user limit must be positive");
        }
    }
}
