package com.philia.flashsale.authentication.session.application.login;

/** Application-facing failure used to distinguish throttle dependency loss from bad credentials. */
public class LoginThrottleUnavailableException extends RuntimeException {
    public LoginThrottleUnavailableException(String message, Throwable cause) { super(message, cause); }
    public LoginThrottleUnavailableException(String message) { super(message); }
}
