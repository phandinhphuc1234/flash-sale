package com.philia.flashsale.order.order.application.exception;

/** Safe application validation failure for bounded owner-query inputs. */
public class InvalidOrderQueryException extends RuntimeException {
    private final String field;

    public InvalidOrderQueryException(String message) {
        super(message);
        this.field = null;
    }

    public InvalidOrderQueryException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
