package com.philia.flashsale.inventory.regularhold.domain.model;

/** Lifecycle owned by Inventory; only HELD consumes regular availability. */
public enum RegularStockHoldStatus {
    HELD,
    CONFIRMED,
    RELEASED,
    EXPIRED
}
