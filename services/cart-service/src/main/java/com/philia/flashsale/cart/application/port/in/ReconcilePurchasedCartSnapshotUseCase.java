package com.philia.flashsale.cart.application.port.in;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;

public interface ReconcilePurchasedCartSnapshotUseCase {
    ReconcilePurchasedCartSnapshotResult reconcile(ReconcilePurchasedCartSnapshotCommand command);
}
