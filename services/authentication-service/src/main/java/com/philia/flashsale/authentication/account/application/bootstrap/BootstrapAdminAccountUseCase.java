package com.philia.flashsale.authentication.account.application.bootstrap;

/** Input port for the explicit operator administrator bootstrap. */
public interface BootstrapAdminAccountUseCase {
    BootstrapAdminAccountResult bootstrap(BootstrapAdminAccountCommand command);
}
