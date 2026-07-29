package com.philia.flashsale.authentication.session.adapter.in.web;

/** Safe HTTP access-token response; refresh credentials remain cookie-only. */
public record TokenResponse(String tokenType, String accessToken, long expiresIn) { }
