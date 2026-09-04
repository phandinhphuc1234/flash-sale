package com.philia.flashsale.order.regularpurchase.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseBusinessException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutBuyNowUseCase;
import com.philia.flashsale.order.regularpurchase.application.result.RegularPurchaseCheckoutResult;
import com.philia.flashsale.order.websupport.context.OrderTraceIdResolver;
import com.philia.flashsale.order.websupport.error.OrderHttpExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Web-contract tests for the authenticated Buy Now ingress. */
class RegularPurchaseControllerTests {
    private static final UUID SHOPPER = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();
    private static final UUID PURCHASE_REQUEST = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final String TRACE = "regular-checkout-test";

    private MockMvc mvc;
    private CheckoutBuyNowUseCase checkout;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        checkout = org.mockito.Mockito.mock(CheckoutBuyNowUseCase.class);
        ObjectProvider<CheckoutBuyNowUseCase> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(checkout);
        OrderTraceIdResolver traceIds = org.mockito.Mockito.mock(OrderTraceIdResolver.class);
        when(traceIds.resolve(any())).thenReturn(TRACE);
        mvc = MockMvcBuilders.standaloneSetup(new RegularPurchaseController(provider, traceIds))
                .setCustomArgumentResolvers(new TestJwtArgumentResolver())
                .setControllerAdvice(new OrderHttpExceptionHandler(traceIds))
                .build();
    }

    @Test
    void newBuyNowUsesAuthenticatedSubjectAndReturnsCreatedLocation() throws Exception {
        when(checkout.checkout(any())).thenReturn(result(false));

        mvc.perform(post("/api/v1/orders/buy-now")
                        .header("Idempotency-Key", "buy-now-001")
                        .header("X-Trace-Id", TRACE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + ORDER))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", TRACE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.purchaseRequestId").value(PURCHASE_REQUEST.toString()))
                .andExpect(jsonPath("$.data.source").value("BUY_NOW"));

        ArgumentCaptor<BuyNowCheckoutCommand> command = ArgumentCaptor.forClass(BuyNowCheckoutCommand.class);
        verify(checkout).checkout(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().shopperId()).isEqualTo(SHOPPER);
        org.assertj.core.api.Assertions.assertThat(command.getValue().variantId()).isEqualTo(VARIANT);
        org.assertj.core.api.Assertions.assertThat(command.getValue().idempotencyKey()).isEqualTo("buy-now-001");
    }

    @Test
    void replayReturnsOriginalResultWithoutLocation() throws Exception {
        when(checkout.checkout(any())).thenReturn(result(true));

        mvc.perform(post("/api/v1/orders/buy-now")
                        .header("Idempotency-Key", "buy-now-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.orderId").value(ORDER.toString()));
    }

    @Test
    void missingIdempotencyKeyUsesStandardBadRequestEnvelope() throws Exception {
        mvc.perform(post("/api/v1/orders/buy-now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IDEMPOTENCY_KEY"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void priceConflictReturnsOnlyApprovedCurrentPriceFacts() throws Exception {
        when(checkout.checkout(any())).thenThrow(new RegularPurchaseBusinessException(
                RegularPurchaseBusinessException.Reason.PRICE_CHANGED, List.of(new ProductPurchaseQuote(
                        VARIANT, true, true, null, UUID.randomUUID(), "SKU-1", "Product", "Variant",
                        Money.of(new BigDecimal("199000.0000")), "VND", 2L))));

        mvc.perform(post("/api/v1/orders/buy-now")
                        .header("Idempotency-Key", "buy-now-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.currentPrices[0].variantId").value(VARIANT.toString()))
                .andExpect(jsonPath("$.currentPrices[0].currentUnitPrice").value(199000));
    }

    private static RegularPurchaseCheckoutResult result(boolean replayed) {
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        return new RegularPurchaseCheckoutResult(PURCHASE_REQUEST, ORDER, PurchaseSource.BUY_NOW,
                "PENDING_PAYMENT", "VND", Money.of(new BigDecimal("179000.0000")),
                now.plusSeconds(600), now.plusSeconds(300), replayed);
    }

    private static String requestJson() {
        return """
                {"variantId":"%s","quantity":1,"expectedUnitPrice":179000.0000,"currency":"VND"}
                """.formatted(VARIANT);
    }

    private static final class TestJwtArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(
                    org.springframework.security.core.annotation.AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("test").header("typ", "at+jwt").subject(SHOPPER.toString()).build();
        }
    }
}
