package com.philia.flashsale.flashsale.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.flashsale.websupport.error.FlashSaleErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class FlashSalePublicSecurityTests {
    @Test
    void unauthorizedResponseIsSanitizedAndTraceable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/flash-sales");
        request.addHeader("X-Trace-Id", "trace-security-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new com.philia.flashsale.flashsale.websupport.error.FlashSaleAuthenticationEntryPoint(
                new ObjectMapper().findAndRegisterModules())
                .commence(request, response, new BadCredentialsException("decoder secret"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals("trace-security-test", response.getHeader("X-Trace-Id"));
        assertTrue(response.getContentAsString().contains("AUTHENTICATION_REQUIRED"));
        assertFalse(response.getContentAsString().contains("decoder"));
    }

    @Test
    void publicErrorCatalogHasApprovedStatusMatrix() {
        assertEquals(400, FlashSaleErrorCode.VALIDATION_FAILED.status().value());
        assertEquals(401, FlashSaleErrorCode.AUTHENTICATION_REQUIRED.status().value());
        assertEquals(403, FlashSaleErrorCode.ACCESS_DENIED.status().value());
        assertEquals(404, FlashSaleErrorCode.FLASH_SALE_RESERVATION_NOT_FOUND.status().value());
        assertEquals(409, FlashSaleErrorCode.FLASH_SALE_SOLD_OUT.status().value());
        assertEquals(503, FlashSaleErrorCode.FLASH_SALE_REDIS_UNAVAILABLE.status().value());
        assertEquals(500, FlashSaleErrorCode.INTERNAL_ERROR.status().value());
    }
}
