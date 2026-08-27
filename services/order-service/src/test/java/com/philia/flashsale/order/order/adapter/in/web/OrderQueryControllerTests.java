package com.philia.flashsale.order.order.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderDetailsResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderSummaryResponse;
import com.philia.flashsale.order.order.application.port.in.GetOwnedOrderUseCase;
import com.philia.flashsale.order.order.application.port.in.ListOwnedOrdersUseCase;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.websupport.context.OrderTraceIdResolver;
import com.philia.flashsale.order.websupport.error.OrderHttpExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderQueryControllerTests {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final String TRACE = "order-query-test";

    private MockMvc mvc;

    private GetOwnedOrderUseCase getOwnedOrder;

    private ListOwnedOrdersUseCase listOwnedOrders;

    private OrderWebMapper mapper;

    private OrderTraceIdResolver traceIds;

    @BeforeEach
    void setUp() {
        getOwnedOrder = org.mockito.Mockito.mock(GetOwnedOrderUseCase.class);
        listOwnedOrders = org.mockito.Mockito.mock(ListOwnedOrdersUseCase.class);
        mapper = org.mockito.Mockito.mock(OrderWebMapper.class);
        traceIds = org.mockito.Mockito.mock(OrderTraceIdResolver.class);
        when(traceIds.resolve(any())).thenReturn(TRACE);
        mvc = MockMvcBuilders.standaloneSetup(new OrderQueryController(getOwnedOrder, listOwnedOrders,
                        mapper, traceIds))
                .setCustomArgumentResolvers(new TestJwtArgumentResolver())
                .setControllerAdvice(new OrderHttpExceptionHandler(traceIds))
                .build();
    }

    @Test
    void detailUsesSharedEnvelopeTraceAndNoStoreWithoutLeakingOwner() throws Exception {
        OrderDetailsResult result = details();
        OrderDetailsResponse response = new OrderDetailsResponse(ORDER, "FS-001", result.purchaseRequestId(),
                result.reservationId(), result.campaignId(), result.status(), result.currency(),
                result.subtotalAmount(), result.totalAmount(), result.acceptedAt(), result.reservationExpiresAt(),
                List.of(), result.createdAt(), result.updatedAt());
        when(getOwnedOrder.get(any())).thenReturn(result);
        when(mapper.toDetailsResponse(result)).thenReturn(response);

        mvc.perform(get("/api/v1/orders/{orderId}", ORDER)
                        .header("X-Trace-Id", TRACE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", TRACE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ORDER.toString()))
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        for (OrderStatus terminalStatus : List.of(
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.EXPIRED)) {
            OrderDetailsResult terminal = details(terminalStatus);
            OrderDetailsResponse terminalResponse = new OrderDetailsResponse(ORDER, "FS-001",
                    terminal.purchaseRequestId(), terminal.reservationId(), terminal.campaignId(),
                    terminal.status(), terminal.currency(), terminal.subtotalAmount(), terminal.totalAmount(),
                    terminal.acceptedAt(), terminal.reservationExpiresAt(), List.of(), terminal.createdAt(),
                    terminal.updatedAt());
            when(getOwnedOrder.get(any())).thenReturn(terminal);
            when(mapper.toDetailsResponse(terminal)).thenReturn(terminalResponse);

            mvc.perform(get("/api/v1/orders/{orderId}", ORDER).header("X-Trace-Id", TRACE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(terminalStatus.name()));
        }
    }

    @Test
    void listRejectsUserIdAndReturnsPageEnvelope() throws Exception {
        OrderPageResult result = new OrderPageResult(List.of(), 0, 20, 0);
        PageResponse<OrderSummaryResponse> response = PageResponse.of(List.of(), 0, 20, 0);
        when(listOwnedOrders.list(any())).thenReturn(result);
        when(mapper.toPageResponse(result)).thenReturn(response);

        mvc.perform(get("/api/v1/orders?userId={owner}", OWNER)
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("userId"));

        mvc.perform(get("/api/v1/orders?page=0&size=20")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.number").value(0))
                .andExpect(jsonPath("$.data.page.size").value(20));
    }

    @Test
    void malformedPathIdIsValidationFailure() throws Exception {
        mvc.perform(get("/api/v1/orders/not-a-uuid")
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static OrderDetailsResult details() {
        return details(OrderStatus.PENDING_PAYMENT);
    }

    private static OrderDetailsResult details(OrderStatus status) {
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        return new OrderDetailsResult(ORDER, "FS-001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                status, "VND", BigDecimal.TEN, BigDecimal.TEN, now, now.plusSeconds(300),
                List.of(), now, now);
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
            return Jwt.withTokenValue("test").header("typ", "at+jwt")
                    .subject(OWNER.toString()).build();
        }
    }
}
