package com.philia.flashsale.campaign.campaign.domain.exception;

/**
 * Typed failure raised when a Campaign invariant or lifecycle rule is violated.
 *
 * <p>The code is a domain code, not an HTTP status. Adapters can translate it
 * into the approved transport contract without leaking web concerns into the
 * Campaign model.</p>
 */
public class CampaignDomainException extends RuntimeException {

    public static final String INVALID_CAMPAIGN_DATA = "CAMPAIGN_VALIDATION_FAILED";
    public static final String INVALID_ITEM_DATA = "CAMPAIGN_VALIDATION_FAILED";
    public static final String INVALID_MONEY = "CAMPAIGN_PRICE_INVALID";
    public static final String INVALID_STATUS = "CAMPAIGN_INVALID_STATUS";
    public static final String START_TIME_IN_PAST = "CAMPAIGN_START_TIME_IN_PAST";
    public static final String ITEM_REQUIRED = "CAMPAIGN_ITEM_REQUIRED";
    public static final String SNAPSHOT_INCOMPLETE = "CAMPAIGN_PRICE_INVALID";
    public static final String ALLOCATION_INCOMPLETE = "CAMPAIGN_VALIDATION_FAILED";
    public static final String INVALID_TRANSITION = "CAMPAIGN_INVALID_STATUS";
    public static final String ACTIVATION_WINDOW_INVALID = "CAMPAIGN_INVALID_STATUS";
    public static final String END_TIME_NOT_REACHED = "CAMPAIGN_INVALID_STATUS";

    private final String code;

    public CampaignDomainException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Campaign domain error code is required");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
