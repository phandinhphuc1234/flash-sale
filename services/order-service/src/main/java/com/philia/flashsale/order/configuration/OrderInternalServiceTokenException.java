package com.philia.flashsale.order.configuration;

/** Safe infrastructure exception: callers must not expose machine-token details to shoppers. */
public final class OrderInternalServiceTokenException extends RuntimeException {
    public OrderInternalServiceTokenException(String message) { super(message); }
    public OrderInternalServiceTokenException(String message, Throwable cause) { super(message, cause); }
}
