package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

/** Sanitized Inventory transport failure produced by the client-specific decoder. */
final class InventoryRemoteException extends RuntimeException {

    private final Failure failure;
    private final int status;

    InventoryRemoteException(Failure failure, int status) {
        super("Inventory downstream request failed");
        this.failure = failure;
        this.status = status;
    }

    Failure failure() {
        return failure;
    }

    int status() {
        return status;
    }

    enum Failure {
        NOT_FOUND,
        INSUFFICIENT_STOCK,
        REQUEST_CONFLICT,
        REJECTED,
        TOKEN_REJECTED,
        UNAVAILABLE
    }
}
