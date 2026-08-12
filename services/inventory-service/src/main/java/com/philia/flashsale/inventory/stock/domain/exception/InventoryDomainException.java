package com.philia.flashsale.inventory.stock.domain.exception;

/** Domain failure raised when a stock invariant would be violated. */
public class InventoryDomainException extends RuntimeException {
    public InventoryDomainException(String message) {
        super(message);
    }
}
