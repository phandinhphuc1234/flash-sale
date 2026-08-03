package com.philia.flashsale.inventory.allocation.domain.exception;

/** Same request id with a different allocation payload is a stable idempotency conflict. */
public class AllocationRequestConflictException extends AllocationDomainException {
    public AllocationRequestConflictException() { super("Request ID was already used for another allocation"); }
}
