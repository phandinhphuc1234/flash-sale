package com.philia.flashsale.authentication.session.application.login;

/** Successful login output containing the access token and one raw refresh value for the cookie adapter. */
public record AuthenticationResult(String tokenType, String accessToken, long expiresIn,
                                   String refreshCredential, long refreshMaxAgeSeconds) { }
