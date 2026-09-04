package com.philia.flashsale.product.catalogquery.application.port.in;

import com.philia.flashsale.product.catalogquery.application.result.PurchaseQuoteResult;
import java.util.List;
import java.util.UUID;

/** Product-owned decision query used only by Order before accepting a regular purchase. */
public interface LookupPurchaseQuotesUseCase {

    List<PurchaseQuoteResult> lookup(List<UUID> variantIds);
}
