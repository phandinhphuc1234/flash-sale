package com.philia.flashsale.order.regularpurchase.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.adapter.in.web.request.BuyNowCheckoutRequest;
import com.philia.flashsale.order.regularpurchase.adapter.in.web.request.CartCheckoutRequest;
import com.philia.flashsale.order.regularpurchase.adapter.in.web.response.RegularPurchaseCheckoutResponse;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutLine;
import com.philia.flashsale.order.regularpurchase.application.exception.InvalidIdempotencyKeyException;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDisabledException;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutBuyNowUseCase;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutCartUseCase;
import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import com.philia.flashsale.order.websupport.context.OrderTraceIdResolver;
import com.philia.flashsale.order.websupport.error.OrderAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated browser ingress for the runtime-gated regular Buy Now workflow. */
@RestController
@RequestMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RegularPurchaseController {

    private final ObjectProvider<CheckoutBuyNowUseCase> checkout;
    private final ObjectProvider<CheckoutCartUseCase> cartCheckout;
    private final OrderTraceIdResolver traceIds;

    @Autowired
    public RegularPurchaseController(ObjectProvider<CheckoutBuyNowUseCase> checkout,
            OrderTraceIdResolver traceIds) {
        this(checkout, null, traceIds);
    }

    public RegularPurchaseController(ObjectProvider<CheckoutBuyNowUseCase> checkout,
            ObjectProvider<CheckoutCartUseCase> cartCheckout, OrderTraceIdResolver traceIds) {
        this.checkout = Objects.requireNonNull(checkout);
        this.cartCheckout = cartCheckout;
        this.traceIds = Objects.requireNonNull(traceIds);
    }

    @PostMapping(path = "/cart-checkouts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<RegularPurchaseCheckoutResponse>> checkoutCart(
            @Valid @RequestBody CartCheckoutRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        validateIdempotencyKey(idempotencyKey);
        CheckoutCartUseCase useCase = cartCheckout == null ? null : cartCheckout.getIfAvailable();
        if (useCase == null) throw new RegularPurchaseDisabledException();
        String traceId = traceIds.resolve(request);
        var lines = body.items().stream().map(line -> new CartCheckoutLine(line.variantId(), line.quantity(),
                line.itemVersion(), Money.of(line.expectedUnitPrice()), line.currency())).toList();
        var result = useCase.checkout(new CartCheckoutCommand(ownerId(jwt), idempotencyKey, body.cartVersion(),
                lines, traceId, validTraceparent(request.getHeader(OrderRequestContext.TRACEPARENT_HEADER)),
                OrderRequestContext.normalizeTracestate(request.getHeader(OrderRequestContext.TRACESTATE_HEADER))));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        HttpHeaders headers = baseHeaders(traceId, result.replayed());
        if (!result.replayed()) headers.setLocation(java.net.URI.create("/api/v1/orders/" + result.orderId()));
        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.success("Cart checkout accepted", RegularPurchaseCheckoutResponse.from(result)));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    @PostMapping(path = "/buy-now", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<RegularPurchaseCheckoutResponse>> buyNow(
            @Valid @RequestBody BuyNowCheckoutRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new InvalidIdempotencyKeyException();
        }
        CheckoutBuyNowUseCase useCase = checkout.getIfAvailable();
        if (useCase == null) throw new RegularPurchaseDisabledException();
        String traceId = traceIds.resolve(request);
        var result = useCase.checkout(new BuyNowCheckoutCommand(ownerId(jwt), idempotencyKey, body.variantId(),
                body.quantity(), Money.of(body.expectedUnitPrice()), body.currency(), traceId,
                validTraceparent(request.getHeader(OrderRequestContext.TRACEPARENT_HEADER)),
                OrderRequestContext.normalizeTracestate(request.getHeader(OrderRequestContext.TRACESTATE_HEADER))));
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        HttpHeaders headers = baseHeaders(traceId, result.replayed());
        if (!result.replayed()) {
            headers.setLocation(java.net.URI.create("/api/v1/orders/" + result.orderId()));
        }
        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.success("Regular purchase accepted", RegularPurchaseCheckoutResponse.from(result)));
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

    private String validTraceparent(String value) {
        return OrderRequestContext.isValidTraceparent(value) ? value : null;
    }

    private HttpHeaders baseHeaders(String traceId, boolean replayed) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(OrderTraceIdResolver.TRACE_HEADER, traceId);
        headers.set("Idempotency-Replayed", Boolean.toString(replayed));
        return headers;
    }
}
