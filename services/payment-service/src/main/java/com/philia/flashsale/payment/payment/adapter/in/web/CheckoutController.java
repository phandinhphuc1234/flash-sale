package com.philia.flashsale.payment.payment.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.payment.payment.adapter.in.web.response.CheckoutSessionResponse;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;
import com.philia.flashsale.payment.payment.application.port.in.StartCheckoutUseCase;
import com.philia.flashsale.payment.websupport.context.PaymentTraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only, no-body Checkout start/resume endpoint. */
@RestController
@RequestMapping("/api/v1/payments")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {"payment.checkout.enabled", "payment.acceptance.enabled",
        "payment.stripe.enabled"}, havingValue = "true")
public class CheckoutController {
    private final StartCheckoutUseCase startCheckout;
    private final CheckoutWebMapper mapper;
    private final PaymentTraceIdResolver traceIds;

    public CheckoutController(StartCheckoutUseCase startCheckout, CheckoutWebMapper mapper,
            PaymentTraceIdResolver traceIds) {
        this.startCheckout = startCheckout;
        this.mapper = mapper;
        this.traceIds = traceIds;
    }

    @PostMapping("/{paymentId}/checkout-sessions")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> start(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) String requestBody,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        if (requestBody != null && !requestBody.isBlank()) {
            throw new IllegalArgumentException("Checkout start does not accept a request body");
        }
        UUID owner = UUID.fromString(jwt.getSubject());
        StartCheckoutResult result = startCheckout.start(new StartCheckoutCommand(paymentId, owner,
                idempotencyKey));
        HttpStatus status = switch (result.outcome()) {
            case CREATED -> HttpStatus.CREATED;
            case REPLAYED -> HttpStatus.OK;
            case RECOVERING -> HttpStatus.ACCEPTED;
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header(PaymentTraceIdResolver.TRACE_HEADER, traceIds.resolve(request))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (result.outcome() == StartCheckoutResult.Outcome.CREATED
                || result.outcome() == StartCheckoutResult.Outcome.REPLAYED) {
            response.header(HttpHeaders.LOCATION, "/api/v1/payments/" + paymentId);
        } else {
            response.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return response.body(ApiResponse.success("Checkout session accepted", mapper.toResponse(result)));
    }
}
