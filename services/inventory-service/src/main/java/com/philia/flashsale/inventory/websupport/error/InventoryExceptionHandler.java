package com.philia.flashsale.inventory.websupport.error;

import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.inventory.allocation.application.exception.AllocationApplicationException;
import com.philia.flashsale.inventory.allocation.domain.exception.AllocationDomainException;
import com.philia.flashsale.inventory.stock.application.exception.StockApplicationException;
import com.philia.flashsale.inventory.stock.domain.exception.InventoryDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {
    @ExceptionHandler({StockApplicationException.class, AllocationApplicationException.class,
            InventoryDomainException.class, AllocationDomainException.class})
    public ResponseEntity<ApiErrorResponse> handleBusiness(RuntimeException exception) {
        boolean notFound = exception.getMessage() != null
                && exception.getMessage().contains("not found");
        String code = notFound ? "INVENTORY_NOT_FOUND" : "INVENTORY_OPERATION_REJECTED";
        HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(code, exception.getMessage()));
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
                ApiErrorResponse.of("VALIDATION_ERROR", exception.getMessage()));
    }
}
