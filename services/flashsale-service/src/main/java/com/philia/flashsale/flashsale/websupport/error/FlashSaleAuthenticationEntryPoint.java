package com.philia.flashsale.flashsale.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Writes a sanitized shared 401 response without leaking decoder or token details. */
@Component
public final class FlashSaleAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public FlashSaleAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        FlashSaleHttpExceptionHandler.writeSecurityError(
                request, response, objectMapper, FlashSaleErrorCode.AUTHENTICATION_REQUIRED, true);
    }
}
