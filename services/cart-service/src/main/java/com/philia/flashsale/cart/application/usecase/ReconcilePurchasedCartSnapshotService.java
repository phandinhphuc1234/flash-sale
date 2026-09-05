package com.philia.flashsale.cart.application.usecase;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.cart.application.port.in.ReconcilePurchasedCartSnapshotUseCase;
import com.philia.flashsale.cart.application.port.out.ReconcilePurchasedCartSnapshotPort;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;
import java.util.Objects;

/** Keeps reconciliation policy in the application layer; SQL remains in the persistence adapter. */
public final class ReconcilePurchasedCartSnapshotService implements ReconcilePurchasedCartSnapshotUseCase {
    private final ReconcilePurchasedCartSnapshotPort persistence;

    public ReconcilePurchasedCartSnapshotService(ReconcilePurchasedCartSnapshotPort persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public ReconcilePurchasedCartSnapshotResult reconcile(ReconcilePurchasedCartSnapshotCommand command) {
        return persistence.apply(Objects.requireNonNull(command, "command"));
    }
}
