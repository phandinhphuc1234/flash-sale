package com.philia.flashsale.campaign.websupport.error;

import com.philia.flashsale.campaign.campaign.domain.exception.CampaignDomainException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignCodeAlreadyExistsException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignNotFoundException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignOperationInProgressException;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignVersionConflictException;
import com.philia.flashsale.campaign.scheduleoperation.application.exception.ScheduleOperationInProgressException;
import com.philia.flashsale.campaign.scheduleoperation.application.exception.ScheduleOperationRequestConflictException;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
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
    ResponseEntity<ApiErrorResponse> domainFailure(
            CampaignDomainException exception, HttpServletRequest request) {
        CampaignErrorCode code = CampaignErrorCode.from(exception.code());
        return error(code, request, null);
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(
            CampaignNotFoundException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_NOT_FOUND, request, null);
    }

    @ExceptionHandler(CampaignCodeAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> duplicateCode(
            CampaignCodeAlreadyExistsException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_CODE_ALREADY_EXISTS, request, null);
    }

    @ExceptionHandler(CampaignVersionConflictException.class)
    ResponseEntity<ApiErrorResponse> staleVersion(
            CampaignVersionConflictException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_VERSION_CONFLICT, request, null);
    }

    @ExceptionHandler(CampaignOperationInProgressException.class)
    ResponseEntity<ApiErrorResponse> operationInProgress(
            CampaignOperationInProgressException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_OPERATION_IN_PROGRESS, request, null);
    }

    @ExceptionHandler(ScheduleOperationInProgressException.class)
    ResponseEntity<ApiErrorResponse> scheduleOperationInProgress(
            ScheduleOperationInProgressException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_OPERATION_IN_PROGRESS, request, null);
    }

    @ExceptionHandler(ScheduleOperationRequestConflictException.class)
    ResponseEntity<ApiErrorResponse> scheduleRequestConflict(
            ScheduleOperationRequestConflictException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_SCHEDULE_REQUEST_CONFLICT, request, null);
    }

    /** Exposes only Campaign-owned failures after outbound adapters sanitize remote details. */
    @ExceptionHandler(CampaignDownstreamException.class)
    ResponseEntity<ApiErrorResponse> downstreamFailure(
            CampaignDownstreamException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.from(exception.failure().name()), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldViolation(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage()))
                .collect(Collectors.toList());
        return error(CampaignErrorCode.CAMPAIGN_VALIDATION_FAILED, request, fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    ResponseEntity<ApiErrorResponse> malformedRequest(
            Exception exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_VALIDATION_FAILED, request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> invalidArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_VALIDATION_FAILED, request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> invalidState(
            IllegalStateException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_INVALID_STATUS, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_METHOD_NOT_ALLOWED, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(CampaignErrorCode.CAMPAIGN_UNSUPPORTED_MEDIA_TYPE, request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(
            Exception exception, HttpServletRequest request) {
        String traceId = CampaignRequestContext.resolveTraceId(request);
        LOG.error("campaign_unexpected_failure traceId={} exceptionType={}",
                traceId, exception.getClass().getSimpleName());
        return error(CampaignErrorCode.CAMPAIGN_INTERNAL_ERROR, request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(
            CampaignErrorCode code,
            HttpServletRequest request,
            List<FieldViolation> fieldErrors) {
        String traceId = CampaignRequestContext.resolveTraceId(request);
        return ResponseEntity.status(code.status())
                .header(CampaignRequestContext.TRACE_HEADER, traceId)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code.name(), code.message(), fieldErrors));
    }
}
