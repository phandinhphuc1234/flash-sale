package com.philia.flashsale.authentication.websupport.error;

/** Stable safe error envelope; internal causes and secrets never cross this boundary. */
public record AuthenticationErrorResponse(String code, String message, String traceId) { }
