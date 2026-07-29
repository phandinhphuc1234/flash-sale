package com.philia.flashsale.authentication.websupport.error;

/** Auth-owned error taxonomy translated to HTTP statuses by the exception handler. */
public enum AuthenticationErrorCode {
    AUTH_VALIDATION_FAILED("Validation failed"),
    AUTH_ACCOUNT_ALREADY_EXISTS("Account already exists"),
    AUTH_INVALID_CREDENTIALS("Invalid credentials"),
    AUTH_TOO_MANY_ATTEMPTS("Too many attempts"),
    AUTH_CROSS_SITE_REQUEST_REJECTED("Cross-site request rejected"),
    AUTH_REFRESH_TOKEN_INVALID("Refresh credential is invalid"),
    AUTH_REFRESH_REUSE_DETECTED("Refresh credential reuse detected"),
    AUTH_METHOD_NOT_ALLOWED("Method not allowed"),
    AUTH_UNSUPPORTED_MEDIA_TYPE("Unsupported media type"),
    AUTH_INTERNAL_ERROR("Authentication service error"),
    AUTHENTICATION_UNAVAILABLE("Authentication service unavailable");

    private final String message;

    AuthenticationErrorCode(String message) { this.message = message; }
    public String message() { return message; }
}
