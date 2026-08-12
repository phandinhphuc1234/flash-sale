package com.philia.flashsale.authentication.account.application.registration;

/** Framework-free registration input produced by the web adapter. */
public record RegisterAccountCommand(String email, String username, String password) { }
