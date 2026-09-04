package com.philia.flashsale.inventory.websupport.error;

import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
import com.philia.flashsale.inventory.allocation.application.exception.AllocationApplicationException;
import com.philia.flashsale.inventory.allocation.domain.exception.AllocationDomainException;
import com.philia.flashsale.inventory.allocation.domain.exception.AllocationRequestConflictException;
import com.philia.flashsale.inventory.stock.domain.exception.InsufficientStockException;
import com.philia.flashsale.inventory.stock.application.exception.StockApplicationException;
import com.philia.flashsale.inventory.stock.domain.exception.InventoryDomainException;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularStockHoldApplicationException;
import com.philia.flashsale.inventory.regularhold.domain.exception.RegularStockHoldDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {
    @ExceptionHandler({StockApplicationException.class, AllocationApplicationException.class,
            InventoryDomainException.class, AllocationDomainException.class,
            RegularStockHoldApplicationException.class, RegularStockHoldDomainException.class})
    public ResponseEntity<ApiErrorResponse> handleBusiness(RuntimeException exception) {
        if (exception instanceof RegularStockHoldApplicationException regularHold) {
            return regularHoldError(regularHold);
        }
        if (exception instanceof InsufficientStockException) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("INVENTORY_INSUFFICIENT_STOCK", "Insufficient available stock"));
        }
        if (exception instanceof AllocationRequestConflictException) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("INVENTORY_ALLOCATION_REQUEST_CONFLICT",
                            "Allocation request id was already used with a different payload"));
        }
        boolean notFound = exception instanceof StockApplicationException stock && stock.isNotFound()
                || exception instanceof AllocationApplicationException allocation && allocation.isNotFound();
        String code = notFound ? "INVENTORY_NOT_FOUND" : "INVENTORY_OPERATION_REJECTED";
        HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        String message = notFound ? "Inventory resource was not found" : "Inventory operation was rejected";
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(code, message));
    }

    private ResponseEntity<ApiErrorResponse> regularHoldError(
            RegularStockHoldApplicationException exception) {
        return switch (exception.reason()) {
            case IDENTITY_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                    "HOLD_IDENTITY_CONFLICT", "Purchase request identity was already used with different hold data"));
            case INVENTORY_ITEM_NOT_FOUND -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                    "INVENTORY_ITEM_NOT_FOUND", "A submitted variant has no Inventory item"));
            case INSUFFICIENT_STOCK -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                    "INSUFFICIENT_STOCK", "Regular stock is insufficient for the submitted purchase",
                    java.util.List.of(new FieldViolation("items", "Requested quantity exceeds current regular availability"))));
            case REQUEST_TIME_OUT_OF_RANGE -> ResponseEntity.badRequest().body(ApiErrorResponse.of(
                    "VALIDATION_ERROR", "Request validation failed",
                    java.util.List.of(new FieldViolation("requestedAt", "must be within the accepted clock-skew bound"))));
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldViolation(
                        fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_ERROR", "Request validation failed", violations));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("VALIDATION_ERROR", "Request validation failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("VALIDATION_ERROR", "Request body is malformed"));
    }
}
