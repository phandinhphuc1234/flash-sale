package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

/** Sanitized Product transport failure produced by the client-specific decoder. */
final class ProductRemoteException extends RuntimeException {

    private final Failure failure;
    private final int status;

    ProductRemoteException(Failure failure, int status) {
        super("Product downstream request failed");
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
        VALIDATION,
        VARIANT_NOT_FOUND,
        VARIANT_NOT_SELLABLE,
        TOKEN_REJECTED,
        UNAVAILABLE
    }
}
