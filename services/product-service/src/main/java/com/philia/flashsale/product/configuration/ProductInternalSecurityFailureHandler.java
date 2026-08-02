package com.philia.flashsale.product.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProductInternalSecurityFailureHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    public ProductInternalSecurityFailureHandler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Internal service authentication is required");
    }
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "CATALOG_READ_SCOPE_REQUIRED", "catalog.read scope is required");
    }
    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message));
    }
}
