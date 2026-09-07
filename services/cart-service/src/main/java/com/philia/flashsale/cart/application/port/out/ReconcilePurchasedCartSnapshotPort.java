package com.philia.flashsale.cart.application.port.out;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;

public interface ReconcilePurchasedCartSnapshotPort {
    ReconcilePurchasedCartSnapshotResult apply(ReconcilePurchasedCartSnapshotCommand command);
}
