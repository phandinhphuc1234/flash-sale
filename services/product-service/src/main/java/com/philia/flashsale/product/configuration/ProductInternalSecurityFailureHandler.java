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
        boolean cartDisplay = request.getRequestURI().endsWith("/display-details");
        boolean purchaseQuote = request.getRequestURI().endsWith("/purchase-quotes");
        write(response, HttpServletResponse.SC_FORBIDDEN,
                cartDisplay ? "CATALOG_VARIANT_DISPLAY_SCOPE_REQUIRED"
                        : purchaseQuote ? "CATALOG_PURCHASE_QUOTE_SCOPE_REQUIRED" : "CATALOG_READ_SCOPE_REQUIRED",
                cartDisplay ? "catalog.variant-display.read scope is required"
                        : purchaseQuote ? "catalog.purchase-quote.read scope is required"
                                : "catalog.read scope is required");
    }
    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message));
    }
}
