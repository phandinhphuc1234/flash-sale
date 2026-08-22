package com.philia.flashsale.authentication.account.application.bootstrap;

/** Framework-free input for the opt-in administrator bootstrap operation. */
public record BootstrapAdminAccountCommand(String email, String username, String password) { }
