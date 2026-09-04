package com.philia.flashsale.inventory.regularhold.adapter.in.web.response;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shopper-safe Order-facing view without Inventory item IDs or SKU operational snapshots. */
public record RegularStockHoldResponse(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        RegularStockHoldStatus status,
        Instant expiresAt,
        List<RegularStockHoldItemResponse> items) { }
