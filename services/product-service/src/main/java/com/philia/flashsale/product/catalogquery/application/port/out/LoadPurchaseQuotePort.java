package com.philia.flashsale.product.catalogquery.application.port.out;

import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.util.List;
import java.util.UUID;

/** Reads the current Product-owned commercial decision projection without reserving any state. */
public interface LoadPurchaseQuotePort {

    List<PurchaseQuoteResult> loadPurchaseQuotes(List<UUID> orderedVariantIds);
}
