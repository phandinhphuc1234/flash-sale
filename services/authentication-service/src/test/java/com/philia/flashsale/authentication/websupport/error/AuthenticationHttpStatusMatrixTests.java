package com.philia.flashsale.authentication.websupport.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.authentication.session.application.login.LoginThrottleUnavailableException;
import com.philia.flashsale.authentication.session.application.login.LoginRateLimitExceededException;
import com.philia.flashsale.authentication.session.application.refresh.RefreshCredentialIssuanceUnavailableException;
import com.philia.flashsale.authentication.session.domain.SessionFailure;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Verifies the stable Auth status matrix and prevents internal details leaking into responses. */
class AuthenticationHttpStatusMatrixTests {

    private final AuthenticationHttpExceptionHandler handler = new AuthenticationHttpExceptionHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE, "trace-status-1");
    }

    @Test
    void mapsCredentialAndThrottleOutcomesToApprovedStatuses() {
        assertThat(handler.accountFailure(new AccountFailure("AUTH_ACCOUNT_ALREADY_EXISTS", "secret"), request))
                .extracting(response -> response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.accountFailure(new AccountFailure("AUTH_INVALID_CREDENTIALS", "password=secret"), request))
                .extracting(response -> response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var rateLimited = handler.rateLimitExceeded(new LoginRateLimitExceededException(73), request);
        assertThat(rateLimited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimited.getHeaders().getFirst("Retry-After")).isEqualTo("73");
        assertThat(rateLimited.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(handler.throttleUnavailable(new LoginThrottleUnavailableException("redis-password=secret"), request))
                .extracting(response -> response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.refreshCredentialIssuanceUnavailable(
                new RefreshCredentialIssuanceUnavailableException(new IllegalStateException("private-key=secret")), request))
                .extracting(response -> response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsSessionAndFrameworkOutcomesWithoutEchoingCauseDetails() {
        var crossSite = handler.sessionFailure(
                new SessionFailure("AUTH_CROSS_SITE_REQUEST_REJECTED", "Origin=https://evil.example"), request);
        var invalidRefresh = handler.sessionFailure(
                new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "token=raw-secret"), request);
        var malformed = handler.malformed(
                new HttpMessageNotReadableException("password=raw-secret", (Throwable) null), request);
        var method = handler.method(new HttpRequestMethodNotSupportedException("TRACE"), request);
        var media = handler.media(new HttpMediaTypeNotSupportedException("application/xml"), request);

        assertThat(crossSite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invalidRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(method.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(media.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(crossSite.getBody()).isEqualTo(new AuthenticationErrorResponse(
                "AUTH_CROSS_SITE_REQUEST_REJECTED", "Cross-site request rejected", "trace-status-1"));
        assertThat(invalidRefresh.getBody().message()).doesNotContain("raw-secret");
        assertThat(malformed.getBody().message()).doesNotContain("raw-secret");
    }

    @Test
    void everyHandlerResponseCarriesTheRequestTraceId() {
        var response = handler.unexpected(new IllegalStateException("private-key=secret"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new AuthenticationErrorResponse(
                "AUTH_INTERNAL_ERROR", "Authentication service error", "trace-status-1"));
    }
}
