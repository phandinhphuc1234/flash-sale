package com.philia.flashsale.campaign.websupport.error;

import org.springframework.http.HttpStatus;

/** Stable Campaign-owned HTTP error taxonomy; internal exception details never cross this boundary. */
public enum CampaignErrorCode {
    CAMPAIGN_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    CAMPAIGN_START_TIME_IN_PAST(HttpStatus.BAD_REQUEST, "Campaign start time is invalid"),
    CAMPAIGN_ITEM_REQUIRED(HttpStatus.BAD_REQUEST, "A Campaign item is required"),
    CAMPAIGN_PRICE_INVALID(HttpStatus.BAD_REQUEST, "Campaign price is invalid"),
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "Campaign was not found"),
    CAMPAIGN_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Campaign code already exists"),
    CAMPAIGN_INVALID_STATUS(HttpStatus.CONFLICT, "Campaign status does not allow this operation"),
    CAMPAIGN_VERSION_CONFLICT(HttpStatus.CONFLICT, "Campaign version is stale"),
    CAMPAIGN_OPERATION_IN_PROGRESS(HttpStatus.CONFLICT, "A Campaign operation is already in progress"),
    CAMPAIGN_SCHEDULE_REQUEST_CONFLICT(HttpStatus.CONFLICT, "Campaign schedule request conflicts with a previous request"),
    PRODUCT_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product variant was not found"),
    PRODUCT_VARIANT_NOT_SELLABLE(HttpStatus.CONFLICT, "Product variant is not sellable"),
    INVENTORY_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Inventory stock is insufficient"),
    INVENTORY_ALLOCATION_REJECTED(HttpStatus.CONFLICT, "Inventory allocation was rejected"),
    INVENTORY_ALLOCATION_CONFLICT(HttpStatus.CONFLICT, "Inventory allocation request conflicts with a previous request"),
    PRODUCT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Product service is temporarily unavailable"),
    INVENTORY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service is temporarily unavailable"),
    CAMPAIGN_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Campaign event was not found"),
    CAMPAIGN_EVENT_INVALID_STATUS(HttpStatus.CONFLICT, "Campaign event cannot be requeued in its current state"),
    CAMPAIGN_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Campaign snapshot was not found"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    CAMPAIGN_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "SCOPE_CAMPAIGN_ADMIN authority is required"),
    CAMPAIGN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),
    CAMPAIGN_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not allowed"),
    CAMPAIGN_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request media type is not supported"),
    CAMPAIGN_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Campaign service error");

    private final HttpStatus status;
    private final String message;

    CampaignErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public static CampaignErrorCode from(String value) {
        try {
            return value == null ? CAMPAIGN_INTERNAL_ERROR : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CAMPAIGN_INTERNAL_ERROR;
        }
    }
}
