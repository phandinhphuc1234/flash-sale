package com.philia.flashsale.authentication.websupport.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;

@Component
/** Creates/propagates X-Trace-Id at the HTTP boundary and echoes it in responses. */
public class AuthenticationTraceFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String candidate = StringUtils.hasText(incoming) ? incoming.trim() : "";
        String traceId = candidate.length() <= 128 && candidate.matches("[A-Za-z0-9._:-]+")
                ? candidate
                : UUID.randomUUID().toString();
        request.setAttribute(AuthenticationRequestContext.TRACE_ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        filterChain.doFilter(request, response);
    }
}
