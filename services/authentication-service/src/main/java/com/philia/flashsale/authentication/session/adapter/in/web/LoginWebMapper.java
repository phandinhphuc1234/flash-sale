package com.philia.flashsale.authentication.session.adapter.in.web;

import com.philia.flashsale.authentication.session.application.login.AuthenticateAccountCommand;
import com.philia.flashsale.authentication.session.application.login.AuthenticationResult;

/** Maps login HTTP DTOs to application models and hides transport details from the use case. */
public final class LoginWebMapper {
    private LoginWebMapper() { }
    public static AuthenticateAccountCommand toCommand(LoginRequest request, String userAgent, String directIp) {
        return new AuthenticateAccountCommand(request.login(), request.password(), request.deviceName(), userAgent, directIp);
    }
    public static TokenResponse toResponse(AuthenticationResult result) {
        return new TokenResponse(result.tokenType(), result.accessToken(), result.expiresIn());
    }
}
