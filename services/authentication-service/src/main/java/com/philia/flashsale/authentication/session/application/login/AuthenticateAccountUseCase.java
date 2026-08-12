package com.philia.flashsale.authentication.session.application.login;

/** Inbound application port for credential authentication. */
public interface AuthenticateAccountUseCase {
    AuthenticationResult authenticate(AuthenticateAccountCommand command);
}
