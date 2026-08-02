package com.philia.flashsale.product.campaignvalidation.application.usecase;

import java.util.UUID;

public class ProductVariantNotSellableException extends RuntimeException {
    public ProductVariantNotSellableException(UUID variantId) { super("Product variant is not sellable: " + variantId); }
}
