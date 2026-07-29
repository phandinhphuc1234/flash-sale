package com.philia.flashsale.authentication.websupport.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.authentication.session.application.login.LoginThrottleUnavailableException;
import com.philia.flashsale.authentication.session.application.login.LoginRateLimitExceededException;
import com.philia.flashsale.authentication.session.application.refresh.RefreshCredentialIssuanceUnavailableException;
import com.philia.flashsale.authentication.session.domain.SessionFailure;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;

@RestControllerAdvice
/** Inbound error translator from domain/application/framework failures to safe HTTP responses. */
public class AuthenticationHttpExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationHttpExceptionHandler.class);
    @ExceptionHandler(AccountFailure.class)
    ResponseEntity<AuthenticationErrorResponse> accountFailure(AccountFailure exception, HttpServletRequest request) {
        AuthenticationErrorCode code = parseCode(exception.code());
        HttpStatus status = switch (code) {
            case AUTH_ACCOUNT_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case AUTH_INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case AUTH_TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, code, request);
    }

    @ExceptionHandler(LoginThrottleUnavailableException.class)
    ResponseEntity<AuthenticationErrorResponse> throttleUnavailable(LoginThrottleUnavailableException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, AuthenticationErrorCode.AUTHENTICATION_UNAVAILABLE, request);
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    ResponseEntity<AuthenticationErrorResponse> rateLimitExceeded(
            LoginRateLimitExceededException exception, HttpServletRequest request) {
        AuthenticationErrorCode code = AuthenticationErrorCode.AUTH_TOO_MANY_ATTEMPTS;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AuthenticationErrorResponse(code.name(), code.message(), traceId(request)));
    }

    @ExceptionHandler(RefreshCredentialIssuanceUnavailableException.class)
    ResponseEntity<AuthenticationErrorResponse> refreshCredentialIssuanceUnavailable(
            RefreshCredentialIssuanceUnavailableException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, AuthenticationErrorCode.AUTHENTICATION_UNAVAILABLE, request);
    }

    @ExceptionHandler(SessionFailure.class)
    ResponseEntity<AuthenticationErrorResponse> sessionFailure(SessionFailure exception, HttpServletRequest request) {
        AuthenticationErrorCode code;
        try { code = AuthenticationErrorCode.valueOf(exception.code()); }
        catch (Exception ignored) { code = AuthenticationErrorCode.AUTH_REFRESH_TOKEN_INVALID; }
        HttpStatus status = code == AuthenticationErrorCode.AUTH_CROSS_SITE_REQUEST_REJECTED
                ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        return error(status, code, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AuthenticationErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, AuthenticationErrorCode.AUTH_VALIDATION_FAILED, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AuthenticationErrorResponse> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, AuthenticationErrorCode.AUTH_VALIDATION_FAILED, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<AuthenticationErrorResponse> method(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, AuthenticationErrorCode.AUTH_METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<AuthenticationErrorResponse> media(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, AuthenticationErrorCode.AUTH_UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<AuthenticationErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        LOG.error("auth_unexpected_failure traceId={} type={} detail={}", traceId,
                exception.getClass().getName(), safeDetail(exception.getMessage()));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, AuthenticationErrorCode.AUTH_INTERNAL_ERROR, request);
    }

    private ResponseEntity<AuthenticationErrorResponse> error(HttpStatus status, AuthenticationErrorCode code, HttpServletRequest request) {
        String traceId = traceId(request);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new AuthenticationErrorResponse(code.name(), code.message(), traceId));
    }

    private String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE));
    }

    private String safeDetail(String message) {
        if (message == null) return "";
        return message.replaceAll("(?i)(password|token|secret|authorization|cookie)[^,;\\s]*", "$1=<redacted>")
                .replaceAll("[\\r\\n\\t]", " ");
    }

    private AuthenticationErrorCode parseCode(String code) {
        try { return AuthenticationErrorCode.valueOf(code); }
        catch (Exception ignored) { return AuthenticationErrorCode.AUTH_INTERNAL_ERROR; }
    }
}
