package com.philia.flashsale.payment.payment.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;
import com.philia.flashsale.payment.payment.application.port.in.StartCheckoutUseCase;
import com.philia.flashsale.payment.websupport.context.PaymentTraceIdResolver;
import com.philia.flashsale.payment.websupport.error.PaymentHttpExceptionHandler;
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

/** MVC contract for status/header semantics and the intentionally empty request body. */
@WebMvcTest(controllers = CheckoutController.class,
        properties = {"payment.checkout.enabled=true", "payment.acceptance.enabled=true",
                "payment.stripe.enabled=true"})
@AutoConfigureMockMvc
@Import({CheckoutWebMapper.class, PaymentTraceIdResolver.class, PaymentHttpExceptionHandler.class})
class CheckoutControllerTests {
    @Autowired MockMvc mvc;
    @MockBean StartCheckoutUseCase useCase;

    @Test
    void createdResponseIs201NoStoreWithLocationAndTrace() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(useCase.start(any())).thenReturn(new StartCheckoutResult(StartCheckoutResult.Outcome.CREATED,
                paymentId, com.philia.flashsale.payment.payment.domain.model.PaymentStatus.PROCESSING,
                "https://checkout.test/ephemeral", Instant.parse("2026-08-17T00:05:00Z")));

        mvc.perform(post("/api/v1/payments/{id}/checkout-sessions", paymentId)
                        .header("Idempotency-Key", "client-key")
                        .header("X-Trace-Id", "trace-g5")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/payments/" + paymentId))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(PaymentTraceIdResolver.TRACE_HEADER, "trace-g5"));
    }

    @Test
    void recoveringResponseIs202WithRetryAfterAndNoProviderUrlInHeaders() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(useCase.start(any())).thenReturn(new StartCheckoutResult(StartCheckoutResult.Outcome.RECOVERING,
                paymentId, com.philia.flashsale.payment.payment.domain.model.PaymentStatus.UNKNOWN, null,
                Instant.parse("2026-08-17T00:05:00Z")));

        mvc.perform(post("/api/v1/payments/{id}/checkout-sessions", paymentId)
                        .header("Idempotency-Key", "client-key")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void nonEmptyRequestBodyIsRejected() throws Exception {
        UUID paymentId = UUID.randomUUID();
        mvc.perform(post("/api/v1/payments/{id}/checkout-sessions", paymentId)
                        .header("Idempotency-Key", "client-key")
                        .contentType("application/json").content("{\"amount\":1}")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }
}
