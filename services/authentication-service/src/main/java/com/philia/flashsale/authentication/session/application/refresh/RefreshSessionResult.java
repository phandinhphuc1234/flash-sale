package com.philia.flashsale.authentication.session.application.refresh;

/** Refresh output used by the web adapter to return a token and replace the cookie. */
public record RefreshSessionResult(String accessToken, String refreshCredential, long expiresIn, long refreshMaxAgeSeconds) { }
