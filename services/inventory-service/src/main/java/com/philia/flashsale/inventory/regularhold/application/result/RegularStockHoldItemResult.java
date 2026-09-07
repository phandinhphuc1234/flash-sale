package com.philia.flashsale.inventory.regularhold.application.result;

import java.util.UUID;

/** HTTP-neutral projection of one canonical regular hold item. */
public record RegularStockHoldItemResult(UUID variantId, long quantity) { }
