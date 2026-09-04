package com.philia.flashsale.order.purchasesaga.domain.model;

/** Selects the owner and command/result branch for the Order Saga's stock participant. */
public enum StockParticipantType {
    FLASH_SALE_RESERVATION,
    REGULAR_STOCK_HOLD
}
