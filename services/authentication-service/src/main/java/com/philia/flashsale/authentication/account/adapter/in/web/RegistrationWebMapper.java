package com.philia.flashsale.authentication.account.adapter.in.web;

import com.philia.flashsale.authentication.account.application.registration.RegisterAccountCommand;
import com.philia.flashsale.authentication.account.application.registration.RegisterAccountResult;

/** Maps registration transport data to application input/output models. */
public final class RegistrationWebMapper {
    private RegistrationWebMapper() { }
    public static RegisterAccountCommand toCommand(RegisterRequest request) {
        return new RegisterAccountCommand(request.getEmail(), request.getUsername(), request.getPassword());
    }
    public static RegisterResponse toResponse(RegisterAccountResult result) {
        return new RegisterResponse(result.userId(), result.email(), result.username(), result.role(), result.status());
    }
}
