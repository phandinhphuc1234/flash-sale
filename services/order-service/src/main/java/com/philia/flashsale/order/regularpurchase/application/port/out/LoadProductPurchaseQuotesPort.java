package com.philia.flashsale.order.regularpurchase.application.port.out;

import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import java.util.List;
import java.util.UUID;

/** Loads a bounded authoritative batch of Product commercial decisions for one regular checkout. */
public interface LoadProductPurchaseQuotesPort {
    List<ProductPurchaseQuote> loadQuotes(List<UUID> variantIds, String traceId);
}
