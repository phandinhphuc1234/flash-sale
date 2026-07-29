package com.philia.flashsale.authentication.websupport.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;

/** Enforces exact trusted Origin/Referer checks for cookie-backed session commands. */
@Component
@ConditionalOnBean(AuthenticationProperties.class)
/** Servlet boundary filter protecting cookie commands with exact trusted-origin checks. */
public class TrustedOriginFilter extends OncePerRequestFilter {
    private final Set<String> trustedOrigins;

    public TrustedOriginFilter(AuthenticationProperties properties) {
        this.trustedOrigins = Arrays.stream(properties.trustedOrigins() == null
                        ? new String[0] : properties.trustedOrigins().split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("/api/v1/auth/refresh".equals(path) || "/api/v1/auth/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank()) source = originFromReferer(request.getHeader("Referer"));
        if (source != null && !source.isBlank() && trustedOrigins.contains(source)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String traceId = String.valueOf(request.getAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE));
        response.getWriter().write("{\"code\":\"AUTH_CROSS_SITE_REQUEST_REJECTED\","
                + "\"message\":\"Cross-site request rejected\","
                + "\"traceId\":\"" + escapeJson(traceId) + "\"}");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            var uri = java.net.URI.create(referer);
            return uri.getScheme() == null || uri.getRawAuthority() == null
                    ? null : uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
