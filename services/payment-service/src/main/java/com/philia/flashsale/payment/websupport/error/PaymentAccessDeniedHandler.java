package com.philia.flashsale.payment.websupport.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Reuses the sanitized authentication writer for an authenticated 403 response. */
@Component
public final class PaymentAccessDeniedHandler implements AccessDeniedHandler {
    private final PaymentAuthenticationEntryPoint writer;

    public PaymentAccessDeniedHandler(PaymentAuthenticationEntryPoint writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writer.write(request, response, PaymentErrorCode.ACCESS_DENIED, false);
    }
}
