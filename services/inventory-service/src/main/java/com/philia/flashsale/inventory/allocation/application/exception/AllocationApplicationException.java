package com.philia.flashsale.inventory.allocation.application.exception;

public class AllocationApplicationException extends RuntimeException {
    private final boolean notFound;

    public AllocationApplicationException(String message) {
        this(message, false);
    }

    private AllocationApplicationException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public static AllocationApplicationException notFound(String message) {
        return new AllocationApplicationException(message, true);
    }

    public boolean isNotFound() {
        return notFound;
    }
}
