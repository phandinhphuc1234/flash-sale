package com.philia.flashsale.order.websupport.error;

/** Sanitized authentication failure for a malformed or non-UUID public JWT subject. */
public class OrderAuthenticationException extends RuntimeException {
    public OrderAuthenticationException() {
        super("Authentication is required");
    }
}
