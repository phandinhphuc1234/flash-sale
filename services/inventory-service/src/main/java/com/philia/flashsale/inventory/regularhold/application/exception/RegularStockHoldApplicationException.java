package com.philia.flashsale.inventory.regularhold.application.exception;

/** Stable business failures translated by Inventory's HTTP adapter without leaking persistence details. */
public final class RegularStockHoldApplicationException extends RuntimeException {
    public enum Reason {
        IDENTITY_CONFLICT,
        INVENTORY_ITEM_NOT_FOUND,
        INSUFFICIENT_STOCK,
        REQUEST_TIME_OUT_OF_RANGE
    }

    private final Reason reason;

    private RegularStockHoldApplicationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public static RegularStockHoldApplicationException identityConflict() {
        return new RegularStockHoldApplicationException(Reason.IDENTITY_CONFLICT);
    }

    public static RegularStockHoldApplicationException inventoryItemNotFound() {
        return new RegularStockHoldApplicationException(Reason.INVENTORY_ITEM_NOT_FOUND);
    }

    public static RegularStockHoldApplicationException insufficientStock() {
        return new RegularStockHoldApplicationException(Reason.INSUFFICIENT_STOCK);
    }

    public static RegularStockHoldApplicationException requestTimeOutOfRange() {
        return new RegularStockHoldApplicationException(Reason.REQUEST_TIME_OUT_OF_RANGE);
    }

    public Reason reason() { return reason; }
}
