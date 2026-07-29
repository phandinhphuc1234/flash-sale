package com.philia.flashsale.authentication.session.application.logout;

/** Inbound application port for revoking the current cookie-backed session. */
public interface LogoutSessionUseCase {
    void logoutCurrent(LogoutCurrentSessionCommand command);
    void logoutAll(LogoutAllSessionsCommand command);
}
