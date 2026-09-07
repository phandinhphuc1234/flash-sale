package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import java.util.List;
import java.util.UUID;

/** Wire request owned by the Order-to-Product adapter. */
public record ProductPurchaseQuoteBatchRequest(List<UUID> variantIds) {
}
