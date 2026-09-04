package com.philia.flashsale.inventory.regularhold.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exact Order-to-Inventory HTTP request; expiry remains Inventory-owned and is intentionally absent. */
public record CreateRegularStockHoldRequest(
        @NotNull UUID holdId,
        @NotNull UUID purchaseRequestId,
        @NotNull UUID orderId,
        @NotNull UUID shopperId,
        @NotNull Instant requestedAt,
        @NotEmpty @Size(max = 20) List<@Valid RegularStockHoldItemRequest> items) { }
