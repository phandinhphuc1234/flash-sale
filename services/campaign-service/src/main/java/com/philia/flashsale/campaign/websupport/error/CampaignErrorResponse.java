package com.philia.flashsale.campaign.websupport.error;

import java.util.List;

/** Stable error body shared by Campaign admin and internal HTTP boundaries. */
public record CampaignErrorResponse(
        String code,
        String message,
        String traceId,
        List<CampaignFieldError> fieldErrors) {

    public CampaignErrorResponse {
        fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
    }
}
