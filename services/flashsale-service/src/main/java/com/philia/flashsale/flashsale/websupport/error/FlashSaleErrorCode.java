package com.philia.flashsale.flashsale.websupport.error;

import org.springframework.http.HttpStatus;

/** Flash Sale-owned HTTP taxonomy; infrastructure details never cross this boundary. */
public enum FlashSaleErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key is required"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    FLASH_SALE_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Reservation was not found"),
    FLASH_SALE_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key conflicts with the request"),
    FLASH_SALE_CAMPAIGN_NOT_ACTIVE(HttpStatus.CONFLICT, "Campaign is not available for purchase"),
    FLASH_SALE_VARIANT_NOT_ELIGIBLE(HttpStatus.CONFLICT, "Variant is not eligible for this Campaign"),
    FLASH_SALE_SOLD_OUT(HttpStatus.CONFLICT, "Campaign quota is sold out"),
    FLASH_SALE_PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Campaign purchase limit was exceeded"),
    FLASH_SALE_RESERVATION_EXPIRED(HttpStatus.CONFLICT, "Reservation has expired"),
    FLASH_SALE_PROJECTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Campaign availability is temporarily unavailable"),
    FLASH_SALE_REDIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Flash Sale is temporarily unavailable"),
    FLASH_SALE_ACCEPTANCE_PENDING(HttpStatus.SERVICE_UNAVAILABLE,
            "The reservation decision is being recovered. Retry with the same idempotency key."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Flash Sale service error");

    private final HttpStatus status;
    private final String message;

    FlashSaleErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
