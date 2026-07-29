package com.philia.flashsale.authentication.websupport.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.observability.AuthenticationHttpMetrics;
import com.philia.flashsale.authentication.throttle.redis.LoginThrottleMetrics;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Verifies trace propagation, safe origin failures, and bounded metric dimensions. */
class AuthenticationObservabilityTests {

    @Test
    void propagatesValidTraceAndReplacesUnsafeTraceHeaders() throws ServletException, IOException {
        AuthenticationTraceFilter filter = new AuthenticationTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext());
        request.addHeader("X-Trace-Id", "trace-client-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-client-1");
        assertThat(request.getAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE))
                .isEqualTo("trace-client-1");

        MockHttpServletRequest forged = new MockHttpServletRequest(new MockServletContext());
        forged.addHeader("X-Trace-Id", "line1\r\nX-Leak: value");
        MockHttpServletResponse forgedResponse = new MockHttpServletResponse();
        filter.doFilter(forged, forgedResponse, new MockFilterChain());

        assertThat(forgedResponse.getHeader("X-Trace-Id")).doesNotContain("\r", "\n", "X-Leak");
        assertThat(forgedResponse.getHeader("X-Trace-Id")).isNotBlank();
    }

    @Test
    void rejectedCookieOriginUsesStableErrorAndTraceContract() throws ServletException, IOException {
        AuthenticationProperties properties = new AuthenticationProperties(
                new AuthenticationProperties.Cookie(false, "Lax", "/api/v1/auth"),
                "https://app.example", new AuthenticationProperties.Throttle(
                        "c2VjcmV0", java.time.Duration.ofMinutes(15), java.time.Duration.ofMinutes(15), 5),
                new AuthenticationProperties.Retention(30), "0 0 3 * * *",
                new AuthenticationProperties.Argon2(16, 32, 1, 19_456, 2));
        TrustedOriginFilter filter = new TrustedOriginFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest(new MockServletContext());
        request.setRequestURI("/api/v1/auth/refresh");
        request.addHeader("Origin", "https://evil.example");
        request.setAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE, "trace-origin-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"code\":\"AUTH_CROSS_SITE_REQUEST_REJECTED\","
                        + "\"message\":\"Cross-site request rejected\","
                        + "\"traceId\":\"trace-origin-1\"}");
    }

    @Test
    void metricsUseBoundedTagsAndNeverIncludeIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthenticationHttpMetrics httpMetrics = new AuthenticationHttpMetrics(registry);
        LoginThrottleMetrics throttleMetrics = new LoginThrottleMetrics(registry);

        var sample = httpMetrics.start();
        httpMetrics.stop(sample, "unknown-operation", 401);
        throttleMetrics.failureRecorded();
        throttleMetrics.unavailable();

        assertThat(registry.find("authentication.http.duration").timer().getId().getTags())
                .extracting(tag -> tag.getValue())
                .contains("other", "401")
                .doesNotContain("alice@example.com", "10.0.0.1", "session-1");
        assertThat(registry.get("authentication.throttle.failures").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("authentication.throttle.unavailable").counter().count()).isEqualTo(1.0);
    }
}
