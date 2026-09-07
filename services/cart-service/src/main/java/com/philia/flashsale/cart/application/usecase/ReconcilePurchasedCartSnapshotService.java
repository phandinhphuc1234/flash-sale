package com.philia.flashsale.cart.application.usecase;

import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.cart.application.port.in.ReconcilePurchasedCartSnapshotUseCase;
import com.philia.flashsale.cart.application.port.out.ReconcilePurchasedCartSnapshotPort;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;
import com.philia.flashsale.cart.observability.CartObservability;
import java.util.Objects;

/** Keeps reconciliation policy in the application layer; SQL remains in the persistence adapter. */
public final class ReconcilePurchasedCartSnapshotService implements ReconcilePurchasedCartSnapshotUseCase {
    private final ReconcilePurchasedCartSnapshotPort persistence;
    private final CartObservability observability;

    public ReconcilePurchasedCartSnapshotService(ReconcilePurchasedCartSnapshotPort persistence) {
        this(persistence, CartObservability.noop());
    }

    public ReconcilePurchasedCartSnapshotService(ReconcilePurchasedCartSnapshotPort persistence,
            CartObservability observability) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public ReconcilePurchasedCartSnapshotResult reconcile(ReconcilePurchasedCartSnapshotCommand command) {
        var result = persistence.apply(Objects.requireNonNull(command, "command"));
        observability.recordReconciliationOutcome(result.outcome().name());
        return result;
    }
}
