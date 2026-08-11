package com.philia.flashsale.flashsale.campaignprojection.domain.exception;

/** Raised when an incoming Campaign snapshot cannot satisfy projection invariants. */
public final class InvalidCampaignProjectionException extends RuntimeException {
    public InvalidCampaignProjectionException(String message) {
        super(message);
    }
}
