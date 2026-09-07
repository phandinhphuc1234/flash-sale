package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

/** Adapter-local Product transport failure with no raw body or token content. */
final class ProductPurchaseQuoteRemoteException extends RuntimeException {

    enum Failure { REJECTED, TOKEN_REJECTED, UNAVAILABLE }

    private final Failure failure;
    private final int status;

    ProductPurchaseQuoteRemoteException(Failure failure, int status) {
        super("Product purchase-quote request failed");
        this.failure = failure;
        this.status = status;
    }

    Failure failure() { return failure; }
    int status() { return status; }
}
