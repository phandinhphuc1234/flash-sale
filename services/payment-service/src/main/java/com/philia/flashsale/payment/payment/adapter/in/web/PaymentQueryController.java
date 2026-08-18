package com.philia.flashsale.payment.payment.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.payment.payment.adapter.in.web.response.PaymentDetailsResponse;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import com.philia.flashsale.payment.payment.application.port.in.GetOwnedPaymentUseCase;
import com.philia.flashsale.payment.websupport.context.PaymentTraceIdResolver;
import com.philia.flashsale.payment.websupport.error.PaymentAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated owner read adapter; the owner is always derived from the trusted JWT subject. */
@RestController
@RequestMapping("/api/v1/payments")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public final class PaymentQueryController {
    private final GetOwnedPaymentUseCase getOwnedPayment;
    private final PaymentQueryWebMapper mapper;
    private final PaymentTraceIdResolver traceIds;

    public PaymentQueryController(GetOwnedPaymentUseCase getOwnedPayment, PaymentQueryWebMapper mapper,
            PaymentTraceIdResolver traceIds) {
        this.getOwnedPayment = getOwnedPayment;
        this.mapper = mapper;
        this.traceIds = traceIds;
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDetailsResponse>> get(
            @PathVariable UUID paymentId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        var result = getOwnedPayment.get(new GetOwnedPaymentQuery(paymentId, ownerId(jwt)));
        return ResponseEntity.ok().headers(headers(request)).body(
                ApiResponse.success("Payment retrieved", mapper.toResponse(result)));
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentDetailsResponse>> getByOrder(
            @PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        var result = getOwnedPayment.getByOrder(new GetOwnedPaymentByOrderQuery(orderId, ownerId(jwt)));
        return ResponseEntity.ok().headers(headers(request)).body(
                ApiResponse.success("Payment retrieved", mapper.toResponse(result)));
    }

    private UUID ownerId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new PaymentAuthenticationException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new PaymentAuthenticationException();
        }
    }

    private HttpHeaders headers(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(PaymentTraceIdResolver.TRACE_HEADER, traceIds.resolve(request));
        return headers;
    }
}
