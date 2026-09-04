package com.philia.flashsale.inventory.regularhold.adapter.in.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** One requested normal-product variant quantity, constrained to the approved 1–10 range. */
public record RegularStockHoldItemRequest(@NotNull UUID variantId, @Min(1) @Max(10) long quantity) { }
