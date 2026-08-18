package com.philia.flashsale.payment.payment.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.payment.payment.application.exception.PaymentNotFoundException;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;
import com.philia.flashsale.payment.payment.application.port.in.GetOwnedPaymentUseCase;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.websupport.context.PaymentTraceIdResolver;
import com.philia.flashsale.payment.websupport.error.PaymentHttpExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** MVC contract for safe owner Payment query responses and non-enumerating errors. */
@WebMvcTest(controllers = PaymentQueryController.class,
        properties = "payment.acceptance.enabled=true")
@AutoConfigureMockMvc
@Import({PaymentQueryWebMapper.class, PaymentTraceIdResolver.class, PaymentHttpExceptionHandler.class})
class PaymentQueryControllerTests {
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;
    @MockBean GetOwnedPaymentUseCase useCase;

    @Test
    void paymentQueryReturnsSafeEnvelopeHeadersAndNoProviderFields() throws Exception {
        when(useCase.get(any())).thenReturn(result());

        mvc.perform(get("/api/v1/payments/{id}", PAYMENT)
                        .header(PaymentTraceIdResolver.TRACE_HEADER, "query-trace")
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(PaymentTraceIdResolver.TRACE_HEADER, "query-trace"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"status\":\"PROCESSING\""),
                        org.hamcrest.Matchers.containsString("\"attemptsUsed\":1"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("checkoutUrl")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider")))));
    }

    @Test
    void orderQueryUsesTheSameSafeResponse() throws Exception {
        when(useCase.getByOrder(any())).thenReturn(result());

        mvc.perform(get("/api/v1/payments/by-order/{id}", ORDER)
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment retrieved")));
    }

    @Test
    void missingAndForeignRowsAreIdentical404s() throws Exception {
        when(useCase.get(any())).thenThrow(new PaymentNotFoundException());

        mvc.perform(get("/api/v1/payments/{id}", PAYMENT)
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"errorCode\":\"PAYMENT_NOT_FOUND\",\"message\":\"Payment not found\"}"));
    }

    @Test
    void invalidUuidIsValidationFailure() throws Exception {
        mvc.perform(get("/api/v1/payments/not-a-uuid")
                        .with(jwt().jwt(token -> token.subject(OWNER.toString()))))
                .andExpect(status().isBadRequest());
    }

    private PaymentDetailsResult result() {
        return new PaymentDetailsResult(PAYMENT, ORDER, new BigDecimal("250000.0000"), "VND",
                PaymentStatus.PROCESSING, Instant.parse("2026-08-17T15:30:00Z"), 1, null,
                Instant.parse("2026-08-17T15:24:58Z"), Instant.parse("2026-08-17T15:25:01Z"));
    }
}
