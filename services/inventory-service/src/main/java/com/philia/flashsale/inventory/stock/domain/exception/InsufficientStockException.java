package com.philia.flashsale.inventory.stock.domain.exception;

/** Typed stock failure used by the campaign allocation HTTP contract. */
public class InsufficientStockException extends InventoryDomainException {
    public InsufficientStockException() { super("Insufficient available stock"); }
}
