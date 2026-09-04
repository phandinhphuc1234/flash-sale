package com.philia.flashsale.product.catalogquery.adapter.in.web;

import com.philia.flashsale.common.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps internal quote input errors without leaking catalog persistence details to Order. */
@RestControllerAdvice(assignableTypes = PurchaseQuoteController.class)
class PurchaseQuoteExceptionHandler {

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ApiErrorResponse> invalidRequest(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "PRODUCT_PURCHASE_QUOTE_VALIDATION_ERROR",
                "Purchase quote request is invalid", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> internalFailure(HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_INTERNAL_ERROR",
                "Product Service could not complete the internal request", request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("Cache-Control", "no-store");
        String trace = request.getHeader("X-Trace-Id");
        if (trace != null && !trace.isBlank()) {
            builder.header("X-Trace-Id", trace);
        }
        return builder.body(ApiErrorResponse.of(code, message));
    }
}
