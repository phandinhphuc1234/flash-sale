package com.philia.flashsale.cart.adapter.out.client.product;

/** Internal Feign classification retained inside the Product adapter boundary. */
final class ProductDisplayRemoteException extends RuntimeException {

    enum Failure { UNAUTHORIZED, FORBIDDEN, SERVER_ERROR, MALFORMED_RESPONSE }

    private final Failure failure;

    ProductDisplayRemoteException(Failure failure) {
        super("Product display request failed");
        this.failure = failure;
    }

    Failure failure() { return failure; }
}
