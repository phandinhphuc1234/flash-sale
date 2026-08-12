package com.philia.flashsale.product.campaignvalidation.application.usecase;

import java.util.UUID;

public class ProductVariantNotFoundException extends RuntimeException {
    public ProductVariantNotFoundException(UUID variantId) { super("Product variant was not found: " + variantId); }
}
