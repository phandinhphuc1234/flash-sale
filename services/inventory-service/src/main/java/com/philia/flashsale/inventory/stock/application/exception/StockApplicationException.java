package com.philia.flashsale.inventory.stock.application.exception;

public class StockApplicationException extends RuntimeException {
    private final boolean notFound;

    public StockApplicationException(String message) {
        this(message, false);
    }

    private StockApplicationException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public static StockApplicationException notFound(String message) {
        return new StockApplicationException(message, true);
    }

    public boolean isNotFound() {
        return notFound;
    }
}
