package com.philia.flashsale.product.catalog.application.port.in;

import com.philia.flashsale.product.catalog.application.result.VariantDisplayResult;
import java.util.List;
import java.util.UUID;

/** Reads the Product-owned display projection for an ordered batch of variant identities. */
public interface LookupVariantDisplaysUseCase {

    List<VariantDisplayResult> lookup(List<UUID> variantIds);
}
