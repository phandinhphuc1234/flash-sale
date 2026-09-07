package com.philia.flashsale.inventory.movement.domain.model;

public enum MovementType {
    STOCK_IN,
    STOCK_ADJUSTED_UP,
    STOCK_ADJUSTED_DOWN,
    CAMPAIGN_ALLOCATED,
    CAMPAIGN_RELEASED,
    CAMPAIGN_RECONCILED,
    REGULAR_HOLD_CONFIRMED
}
