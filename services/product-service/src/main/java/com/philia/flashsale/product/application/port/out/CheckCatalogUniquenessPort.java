package com.philia.flashsale.product.application.port.out;

public interface CheckCatalogUniquenessPort {

    boolean productCodeExists(String code);

    boolean productSlugExists(String slug);

    boolean variantSkuExists(String sku);

    boolean barcodeExists(String barcode);
}
