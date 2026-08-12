package com.philia.flashsale.campaign.scheduleoperation.domain.model;

/** Durable states for the idempotent Campaign-to-Inventory scheduling workflow. */
public enum ScheduleOperationStatus {
    STARTED,
    INVENTORY_ALLOCATED,
    COMPLETED,
    FAILED;

    /** Returns whether the state change is one of the transitions approved for Feature 017. */
    public boolean canTransitionTo(ScheduleOperationStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case STARTED -> target == INVENTORY_ALLOCATED || target == FAILED;
            case INVENTORY_ALLOCATED -> target == COMPLETED;
            case FAILED -> target == STARTED;
            case COMPLETED -> false;
        };
    }

    public boolean isInFlight() {
        return this == STARTED || this == INVENTORY_ALLOCATED;
    }
}
