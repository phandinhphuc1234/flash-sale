package com.philia.flashsale.flashsale.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes a sanitized shared 403 response for authenticated callers without required access. */
@Component
public final class FlashSaleAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public FlashSaleAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        FlashSaleHttpExceptionHandler.writeSecurityError(
                request, response, objectMapper, FlashSaleErrorCode.ACCESS_DENIED, false);
    }
}
