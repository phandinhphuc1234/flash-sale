package com.philia.flashsale.inventory.websupport.error;

import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.inventory.allocation.application.exception.AllocationApplicationException;
import com.philia.flashsale.inventory.allocation.domain.exception.AllocationDomainException;
import com.philia.flashsale.inventory.allocation.domain.exception.AllocationRequestConflictException;
import com.philia.flashsale.inventory.stock.domain.exception.InsufficientStockException;
import com.philia.flashsale.inventory.stock.application.exception.StockApplicationException;
import com.philia.flashsale.inventory.stock.domain.exception.InventoryDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {
    @ExceptionHandler({StockApplicationException.class, AllocationApplicationException.class,
            InventoryDomainException.class, AllocationDomainException.class})
    public ResponseEntity<ApiErrorResponse> handleBusiness(RuntimeException exception) {
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new com.philia.flashsale.common.web.FieldViolation(
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
