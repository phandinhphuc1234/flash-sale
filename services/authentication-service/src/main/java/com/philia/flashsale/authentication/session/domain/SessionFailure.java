package com.philia.flashsale.authentication.session.domain;

/** Domain failure for invalid, expired, revoked, or replayed session credentials. */
public class SessionFailure extends RuntimeException {
    private final String code;

    public SessionFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
