package com.philia.flashsale.product.campaignvalidation.adapter.in.web;

import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.product.campaignvalidation.application.usecase.ProductVariantNotFoundException;
import com.philia.flashsale.product.campaignvalidation.application.usecase.ProductVariantNotSellableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CampaignValidationController.class)
class CampaignValidationExceptionHandler {
    @ExceptionHandler(ProductVariantNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ProductVariantNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_VARIANT_NOT_FOUND", "Product variant was not found", request);
    }

    @ExceptionHandler(ProductVariantNotSellableException.class)
    ResponseEntity<ApiErrorResponse> notSellable(ProductVariantNotSellableException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PRODUCT_VARIANT_NOT_SELLABLE", "Product variant is not sellable", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_CAMPAIGN_VALIDATION_REQUEST", "Campaign validation request is invalid", request);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        String trace = request.getHeader("X-Trace-Id");
        if (trace != null && !trace.isBlank()) builder.header("X-Trace-Id", trace);
        return builder.body(ApiErrorResponse.of(code, message));
    }
}
