package com.philia.flashsale.authentication.session.application.refresh;

/** Signals that rotation committed but the replacement access token could not be signed. */
public class RefreshCredentialIssuanceUnavailableException extends RuntimeException {
    public RefreshCredentialIssuanceUnavailableException(Throwable cause) {
        super("Replacement credentials could not be issued", cause);
    }
}
