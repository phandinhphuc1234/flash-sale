package com.philia.flashsale.cart.application.port.in;

import com.philia.flashsale.cart.application.query.GetCartCheckoutSnapshotQuery;
import com.philia.flashsale.cart.application.result.CartCheckoutSnapshotResult;

public interface GetCartCheckoutSnapshotUseCase {
    CartCheckoutSnapshotResult get(GetCartCheckoutSnapshotQuery query);
}
