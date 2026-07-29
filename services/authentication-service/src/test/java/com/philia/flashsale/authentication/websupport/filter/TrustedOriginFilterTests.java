package com.philia.flashsale.authentication.websupport.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedOriginFilterTests {

    private final TrustedOriginFilter filter = new TrustedOriginFilter(properties());

    @Test
    void rejectsRefreshWhenBothOriginAndRefererAreMissing() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/v1/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void acceptsAProtectedRequestFromTheExactTrustedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/v1/auth/logout");
        request.addHeader("Origin", "https://app.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void doesNotApplyCookieOriginPolicyToBearerProtectedLogoutAll() throws ServletException, IOException {
        MockHttpServletRequest request = request("/api/v1/auth/logout-all");
        request.addHeader("Origin", "https://untrusted.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE, "trace-origin-test");
        return request;
    }

    private AuthenticationProperties properties() {
        return new AuthenticationProperties(
                new AuthenticationProperties.Cookie(false, "Lax", "/api/v1/auth"),
                "https://app.example",
                new AuthenticationProperties.Throttle("secret", Duration.ofMinutes(15), Duration.ofMinutes(15), 5),
                new AuthenticationProperties.Retention(30),
                "0 0 3 * * *",
                new AuthenticationProperties.Argon2(16, 32, 1, 19_456, 2));
    }
}
