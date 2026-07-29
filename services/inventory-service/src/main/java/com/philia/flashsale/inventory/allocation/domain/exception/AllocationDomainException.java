package com.philia.flashsale.inventory.allocation.domain.exception;

/** Domain failure for campaign allocation lifecycle rules. */
public class AllocationDomainException extends RuntimeException {
    public AllocationDomainException(String message) {
        super(message);
    }
}
