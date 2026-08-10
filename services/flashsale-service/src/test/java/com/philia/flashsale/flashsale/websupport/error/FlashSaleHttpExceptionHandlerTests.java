package com.philia.flashsale.flashsale.websupport.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class FlashSaleHttpExceptionHandlerTests {
    @Test
    void classifierNeverExposesUnexpectedInfrastructureDetails() {
        var exception = new RuntimeException("redis password=secret internal stack");
        assertEquals(FlashSaleErrorCode.INTERNAL_ERROR, FlashSaleExceptionClassifier.classify(exception));
    }

    @Test
    void errorEnvelopeUsesHeaderOnlyTraceIdentityAndNoStore() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "error-trace");
        var handler = new FlashSaleHttpExceptionHandler(new ObjectMapper());
        var method = FlashSaleHttpExceptionHandler.class.getDeclaredMethod(
                "error", FlashSaleErrorCode.class, HttpServletRequest.class, java.util.List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiErrorResponse> response = (ResponseEntity<ApiErrorResponse>) method.invoke(
                handler, FlashSaleErrorCode.FLASH_SALE_SOLD_OUT, request, null);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("error-trace", response.getHeaders().getFirst("X-Trace-Id"));
        assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
        assertTrue(response.getBody().errorCode().startsWith("FLASH_SALE_"));
    }
}
