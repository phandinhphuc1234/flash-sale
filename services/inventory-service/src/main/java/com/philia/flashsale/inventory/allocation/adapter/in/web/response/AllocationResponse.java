package com.philia.flashsale.inventory.allocation.adapter.in.web.response;

import java.util.UUID;

/** HTTP representation of a campaign stock allocation. */
public record AllocationResponse(
        UUID id,
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long allocatedQuantity,
        long soldQuantity,
        long returnedQuantity,
        String status
) {
}
