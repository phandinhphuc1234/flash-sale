package com.philia.flashsale.order.order.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderDetailsResponse;
import com.philia.flashsale.order.order.adapter.in.web.response.OrderSummaryResponse;
import com.philia.flashsale.order.order.application.port.in.GetOwnedOrderUseCase;
import com.philia.flashsale.order.order.application.port.in.ListOwnedOrdersUseCase;
import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.websupport.context.OrderTraceIdResolver;
import com.philia.flashsale.order.websupport.error.OrderAuthenticationException;
import com.philia.flashsale.order.order.application.exception.InvalidOrderQueryException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read-only Order HTTP adapter; ownership is always derived from the JWT subject. */
@RestController
@RequestMapping("/api/v1/orders")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(GetOwnedOrderUseCase.class)
public class OrderQueryController {
    private static final Set<String> LIST_PARAMETERS = Set.of("page", "size");
    private final GetOwnedOrderUseCase getOwnedOrder;
    private final ListOwnedOrdersUseCase listOwnedOrders;
    private final OrderWebMapper mapper;
    private final OrderTraceIdResolver traceIds;

    public OrderQueryController(GetOwnedOrderUseCase getOwnedOrder, ListOwnedOrdersUseCase listOwnedOrders,
            OrderWebMapper mapper, OrderTraceIdResolver traceIds) {
        this.getOwnedOrder = getOwnedOrder;
        this.listOwnedOrders = listOwnedOrders;
        this.mapper = mapper;
        this.traceIds = traceIds;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailsResponse>> get(
            @PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        rejectUnsupportedParameters(request, Set.of());
        OrderDetailsResult result = getOwnedOrder.get(new GetOwnedOrderQuery(orderId, ownerId(jwt)));
        return ResponseEntity.ok().headers(headers(request)).body(
                ApiResponse.success("Order retrieved", mapper.toDetailsResponse(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        rejectUnsupportedParameters(request, LIST_PARAMETERS);
        int page = parseInt(request.getParameter("page"), 0, "page");
        int size = parseInt(request.getParameter("size"), 20, "size");
        OrderPageResult result = listOwnedOrders.list(new ListOwnedOrdersQuery(ownerId(jwt), page, size));
        return ResponseEntity.ok().headers(headers(request)).body(
                ApiResponse.success("Orders retrieved", mapper.toPageResponse(result)));
    }

    private UUID ownerId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new OrderAuthenticationException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new OrderAuthenticationException();
        }
    }

    private int parseInt(String value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidOrderQueryException(field + " must be an integer", field);
        }
    }

    private void rejectUnsupportedParameters(HttpServletRequest request, Set<String> allowed) {
        for (String parameter : request.getParameterMap().keySet()) {
            if (!allowed.contains(parameter)) {
                throw new InvalidOrderQueryException("Unsupported query parameter", parameter);
            }
        }
    }

    private HttpHeaders headers(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(OrderTraceIdResolver.TRACE_HEADER, traceIds.resolve(request));
        return headers;
    }
}
