package com.philia.flashsale.campaign.websupport.error;

import com.philia.flashsale.campaign.campaign.domain.exception.CampaignDomainException;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Translates Campaign/domain/framework failures into a safe, traceable HTTP contract. */
@RestControllerAdvice
public class CampaignHttpExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignHttpExceptionHandler.class);

    @ExceptionHandler(CampaignDomainException.class)
    ResponseEntity<CampaignErrorResponse> domainFailure(
            CampaignDomainException exception, HttpServletRequest request) {
        CampaignErrorCode code = CampaignErrorCode.from(exception.code());
        return error(code, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<CampaignErrorResponse> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<CampaignFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new CampaignFieldError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage()))
                .collect(Collectors.toList());
        return error(CampaignErrorCode.CAMPAIGN_VALIDATION_FAILED, request, fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ResponseEntity<CampaignErrorResponse> malformedRequest(
            Exception exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_VALIDATION_FAILED, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<CampaignErrorResponse> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_METHOD_NOT_ALLOWED, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<CampaignErrorResponse> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_UNSUPPORTED_MEDIA_TYPE, request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<CampaignErrorResponse> unexpected(
            Exception exception, HttpServletRequest request) {
        String traceId = CampaignRequestContext.resolveTraceId(request);
        LOG.error("campaign_unexpected_failure traceId={} exceptionType={}",
                traceId, exception.getClass().getSimpleName());
        return error(CampaignErrorCode.CAMPAIGN_INTERNAL_ERROR, request, null);
    }

    private ResponseEntity<CampaignErrorResponse> error(
            CampaignErrorCode code,
            HttpServletRequest request,
            List<CampaignFieldError> fieldErrors) {
        String traceId = CampaignRequestContext.resolveTraceId(request);
        return ResponseEntity.status(code.status())
                .header(CampaignRequestContext.TRACE_HEADER, traceId)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CampaignErrorResponse(code.name(), code.message(), traceId, fieldErrors));
    }
}
