package com.philia.flashsale.campaign.security.serviceidentity;

/** Signals that Campaign could not obtain a service credential for a downstream call. */
public final class CampaignServiceTokenException extends RuntimeException {

    public CampaignServiceTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public CampaignServiceTokenException(String message) {
        super(message);
    }
}
