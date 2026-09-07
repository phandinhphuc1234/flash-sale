package com.philia.flashsale.order.regularpurchase.adapter.out.client.cart;

final class CartCheckoutSnapshotRemoteException extends RuntimeException {
    private final int status;

    CartCheckoutSnapshotRemoteException(int status) {
        this.status = status;
    }

    int status() { return status; }
}
