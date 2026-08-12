package com.philia.flashsale.authentication.session.application.login;

/** Outbound capability for login cooldown and rolling-window decisions. */
public interface LoginThrottlePort {
    void beforeAttempt(String normalizedLogin);
    void recordFailure(String normalizedLogin);
    void beforeCommit(String normalizedLogin);
}
