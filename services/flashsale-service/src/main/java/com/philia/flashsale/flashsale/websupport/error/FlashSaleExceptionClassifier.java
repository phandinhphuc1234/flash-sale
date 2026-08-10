package com.philia.flashsale.flashsale.websupport.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** Converts known boundary failures into the stable Flash Sale error catalog. */
public final class FlashSaleExceptionClassifier {
    private FlashSaleExceptionClassifier() {
    }

    public static FlashSaleErrorCode classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResponseStatusException statusException) {
                return fromStatus(statusException.getStatusCode());
            }
            String name = current.getClass().getSimpleName();
            if (name.contains("Idempotency") && name.contains("Conflict")) {
                return FlashSaleErrorCode.FLASH_SALE_IDEMPOTENCY_CONFLICT;
            }
            if (name.contains("SoldOut")) {
                return FlashSaleErrorCode.FLASH_SALE_SOLD_OUT;
            }
            if (name.contains("PurchaseLimit")) {
                return FlashSaleErrorCode.FLASH_SALE_PURCHASE_LIMIT_EXCEEDED;
            }
            if (name.contains("Reservation") && name.contains("Expired")) {
                return FlashSaleErrorCode.FLASH_SALE_RESERVATION_EXPIRED;
            }
            current = current.getCause();
        }
        return FlashSaleErrorCode.INTERNAL_ERROR;
    }

    private static FlashSaleErrorCode fromStatus(HttpStatusCode status) {
        if (status.equals(HttpStatus.BAD_REQUEST)) {
            return FlashSaleErrorCode.VALIDATION_FAILED;
        }
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return FlashSaleErrorCode.FLASH_SALE_RESERVATION_NOT_FOUND;
        }
        if (status.equals(HttpStatus.CONFLICT)) {
            return FlashSaleErrorCode.FLASH_SALE_CAMPAIGN_NOT_ACTIVE;
        }
        return FlashSaleErrorCode.INTERNAL_ERROR;
    }
}
