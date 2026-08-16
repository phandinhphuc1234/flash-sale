package com.philia.flashsale.order.websupport.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes the stable public 403 envelope for authenticated-but-forbidden calls. */
@Component
public class OrderAccessDeniedHandler implements AccessDeniedHandler {
    private final OrderAuthenticationEntryPoint writer;

    public OrderAccessDeniedHandler(OrderAuthenticationEntryPoint writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writer.write(request, response, OrderErrorCode.ACCESS_DENIED, false);
    }
}
