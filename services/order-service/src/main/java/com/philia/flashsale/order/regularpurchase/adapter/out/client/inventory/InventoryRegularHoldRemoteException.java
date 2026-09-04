package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

/** Adapter-local Inventory transport failure with sanitized, status-derived classification only. */
final class InventoryRegularHoldRemoteException extends RuntimeException {

    enum Failure { INSUFFICIENT_STOCK, ITEM_NOT_FOUND, IDENTITY_CONFLICT, REJECTED, TOKEN_REJECTED, UNAVAILABLE }

    private final Failure failure;
    private final int status;

    InventoryRegularHoldRemoteException(Failure failure, int status) {
        super("Inventory regular hold request failed");
        this.failure = failure;
        this.status = status;
    }

    Failure failure() { return failure; }
    int status() { return status; }
}
