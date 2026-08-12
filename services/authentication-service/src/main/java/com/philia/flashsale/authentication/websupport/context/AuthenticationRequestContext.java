package com.philia.flashsale.authentication.websupport.context;

/** Constants for request-scoped trace context shared by filters and error responses. */
public final class AuthenticationRequestContext {
    public static final String TRACE_ATTRIBUTE = AuthenticationRequestContext.class.getName() + ".traceId";
    private AuthenticationRequestContext() { }
}
