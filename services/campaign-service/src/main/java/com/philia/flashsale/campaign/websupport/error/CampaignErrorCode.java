package com.philia.flashsale.campaign.websupport.error;

import org.springframework.http.HttpStatus;

/**
 * Stable Campaign-owned HTTP error taxonomy.
 * <p>
 * Ensures internal exception details never cross the service boundary
 * by mapping domain failures to explicit HTTP statuses and client messages.
 * </p>
 */
public enum CampaignErrorCode {

    // =========================================================================
    // 1. Client Request Validation Failures (HTTP 400 - BAD REQUEST)
    // =========================================================================
    /**
     * Triggered when the incoming request payload violates basic validation rules.
     */
    CAMPAIGN_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),

    /** Triggered when the campaign start time is configured in the past. */
    CAMPAIGN_START_TIME_IN_PAST(HttpStatus.BAD_REQUEST, "Campaign start time is invalid"),

    /**
     * Triggered when attempting to schedule or activate a campaign without a
     * product item.
     */
    CAMPAIGN_ITEM_REQUIRED(HttpStatus.BAD_REQUEST, "A Campaign item is required"),

    /**
     * Triggered when the promotional price is negative or exceeds the original
     * price.
     */
    CAMPAIGN_PRICE_INVALID(HttpStatus.BAD_REQUEST, "Campaign price is invalid"),

    // =========================================================================
    // 2. Business Lifecycle & State Conflicts (HTTP 409 - CONFLICT)
    // =========================================================================
    /** Triggered when a campaign code is already taken. */
    CAMPAIGN_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Campaign code already exists"),

    /**
     * Triggered when an operation is invalid for the current campaign status (e.g.,
     * editing an ENDED campaign).
     */
    CAMPAIGN_INVALID_STATUS(HttpStatus.CONFLICT, "Campaign status does not allow this operation"),

    /**
     * Triggered when concurrent modifications detect a stale optimistic locking
     * version.
     */
    CAMPAIGN_VERSION_CONFLICT(HttpStatus.CONFLICT, "Campaign version is stale"),

    /**
     * Triggered when a background or schedule operation is already active for this
     * campaign.
     */
    CAMPAIGN_OPERATION_IN_PROGRESS(HttpStatus.CONFLICT, "A Campaign operation is already in progress"),

    /**
     * Triggered when an idempotency key conflicts with a previously processed
     * schedule request.
     */
    CAMPAIGN_SCHEDULE_REQUEST_CONFLICT(HttpStatus.CONFLICT,
            "Campaign schedule request conflicts with a previous request"),

    /**
     * Triggered when the selected product variant is marked as non-sellable in
     * Catalog Service.
     */
    PRODUCT_VARIANT_NOT_SELLABLE(HttpStatus.CONFLICT, "Product variant is not sellable"),

    /**
     * Triggered when Inventory Service does not have enough stock for the campaign
     * allocation.
     */
    INVENTORY_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Inventory stock is insufficient"),

    /** Triggered when Inventory Service rejects the allocation request. */
    INVENTORY_ALLOCATION_REJECTED(HttpStatus.CONFLICT, "Inventory allocation was rejected"),

    /**
     * Triggered when an inventory allocation request conflicts with an existing
     * reservation.
     */
    INVENTORY_ALLOCATION_CONFLICT(HttpStatus.CONFLICT,
            "Inventory allocation request conflicts with a previous request"),

    /**
     * Triggered when a transactional outbox event cannot be requeued due to its
     * state.
     */
    CAMPAIGN_EVENT_INVALID_STATUS(HttpStatus.CONFLICT, "Campaign event cannot be requeued in its current state"),

    // =========================================================================
    // 3. Resource Not Found Failures (HTTP 404 - NOT FOUND)
    // =========================================================================
    /**
     * Triggered when the requested product variant ID does not exist in Catalog
     * Service.
     */
    PRODUCT_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product variant was not found"),

    /** Triggered when an outbox event ID is missing. */
    CAMPAIGN_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Campaign event was not found"),

    /** Triggered when a campaign snapshot artifact cannot be found. */
    CAMPAIGN_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Campaign snapshot was not found"),

    // =========================================================================
    // 4. Security & Authorization Failures (HTTP 401 & 403)
    // =========================================================================
    /** Triggered when the JWT token is missing, expired, or invalid. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),

    /**
     * Triggered when the caller lacks the required 'SCOPE_CAMPAIGN_ADMIN'
     * authority.
     */
    CAMPAIGN_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "SCOPE_CAMPAIGN_ADMIN authority is required"),

    /**
     * Triggered when the caller is authenticated but forbidden from accessing the
     * resource.
     */
    CAMPAIGN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied"),

    // =========================================================================
    // 5. HTTP Protocol Misconfigurations (HTTP 405 & 415)
    // =========================================================================
    /**
     * Triggered when using an unsupported HTTP method (e.g., POST instead of GET).
     */
    CAMPAIGN_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not allowed"),

    /**
     * Triggered when the Content-Type header is unsupported (e.g., text/plain
     * instead of application/json).
     */
    CAMPAIGN_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request media type is not supported"),

    // =========================================================================
    // 6. Infrastructure & Downstream Failures (HTTP 500 & 503)
    // =========================================================================
    /** Triggered when Product Service fails or times out. */
    PRODUCT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Product service is temporarily unavailable"),

    /** Triggered when Inventory Service fails or times out. */
    INVENTORY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service is temporarily unavailable"),

    /** Default fallback error for unexpected internal server errors. */
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

    /**
     * Safely maps a String error code to its enum representation.
     * Fallbacks to {@link #CAMPAIGN_INTERNAL_ERROR} if the value is null or
     * unrecognized.
     *
     * @param value String representation of the error code
     * @return Resolved {@link CampaignErrorCode}
     */
    public static CampaignErrorCode from(String value) {
        try {
            return value == null ? CAMPAIGN_INTERNAL_ERROR : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CAMPAIGN_INTERNAL_ERROR;
        }
    }
}