package com.philia.flashsale.campaign.campaign.application.exception;

/** Raised when a normalized Campaign code is already owned by another Campaign. */
public final class CampaignCodeAlreadyExistsException extends RuntimeException {

    public CampaignCodeAlreadyExistsException(String code) {
        super("Campaign code already exists: " + code);
    }
}
