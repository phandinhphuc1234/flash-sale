package com.philia.flashsale.cart.application.result;

/** Outcome of an idempotent, conditional Cart cleanup. */
public record ReconcilePurchasedCartSnapshotResult(Outcome outcome, int removedItemCount) {
    public enum Outcome { APPLIED, PARTIAL_NOOP, REPLAYED, CONFLICT }
}
