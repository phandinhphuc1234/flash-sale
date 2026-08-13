package com.philia.flashsale.campaign.websupport.error;

/**
 * Safe field-level validation detail exposed without framework exception names.
 */
public record CampaignFieldError(
        String field,
        String message) {
}
