package com.philia.flashsale.authentication.account.application.registration;

/** Inbound application port for creating a normal shopper account. */
public interface RegisterAccountUseCase {
    RegisterAccountResult register(RegisterAccountCommand command);
}
