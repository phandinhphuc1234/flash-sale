package com.philia.flashsale.authentication.session.application.logout;

/** Inbound application port for revoking every session owned by a verified subject. */
public interface LogoutAllSessionsUseCase {
    void logoutAll(LogoutAllSessionsCommand command);
}
