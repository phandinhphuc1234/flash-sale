package com.philia.flashsale.inventory.regularhold.application.model;

import java.util.UUID;

/** Public contract subset of a durable hold line; no Inventory internal identifiers are carried out. */
public record RegularHoldFactItem(UUID variantId, long quantity) {
}
