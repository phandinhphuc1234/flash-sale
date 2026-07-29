package com.philia.flashsale.authentication.websupport.error;

/** Stable success envelope shared by Authentication HTTP endpoints. */
public record AuthenticationApiResponse<T>(T data, String traceId) { }
