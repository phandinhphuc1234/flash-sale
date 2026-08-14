package com.philia.flashsale.flashsale.reservation.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.mapper.ReservationWebMapper;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.request.ReserveCampaignQuotaRequest;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.response.ReservationAcceptedResponse;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.response.ReservationResponse;
import com.philia.flashsale.flashsale.reservation.application.command.SubmitReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.exception.ReservationSubmissionException;
import com.philia.flashsale.flashsale.reservation.application.port.in.GetOwnedReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.in.SubmitReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.query.GetOwnedReservationQuery;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDetailsResult;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;
import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import com.philia.flashsale.flashsale.websupport.error.FlashSaleInvalidIdempotencyKeyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public reservation boundary; no business rules or persistence types cross this adapter. */
@RestController
@RequestMapping("/api/v1/flash-sales")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FlashSaleReservationController {
    private final SubmitReservationUseCase submitReservation;
    private final GetOwnedReservationUseCase getOwnedReservation;
    private final ReservationWebMapper mapper;

    public FlashSaleReservationController(SubmitReservationUseCase submitReservation,
            GetOwnedReservationUseCase getOwnedReservation, ReservationWebMapper mapper) {
        this.submitReservation = submitReservation;
        this.getOwnedReservation = getOwnedReservation;
        this.mapper = mapper;
    }

    @GetMapping("/reservations/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getOwnedReservation(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ReservationDetailsResult result = getOwnedReservation
                .getOwnedReservation(new GetOwnedReservationQuery(reservationId, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String traceId = FlashSaleRequestContext.resolveTraceId(servletRequest);
        return ResponseEntity.ok()
                .header(FlashSaleRequestContext.TRACE_HEADER, traceId)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success("Reservation retrieved", mapper.toReservationResponse(result)));
    }

    @PostMapping("/{campaignId}/reservations")
    public ResponseEntity<ApiResponse<ReservationAcceptedResponse>> submit(
            @PathVariable UUID campaignId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = FlashSaleRequestContext.TRACEPARENT_HEADER, required = false)
            String traceparent,
            @RequestHeader(value = "tracestate", required = false) String tracestate,
            @Valid @RequestBody ReserveCampaignQuotaRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new FlashSaleInvalidIdempotencyKeyException();
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        SubmitReservationCommand command = mapper.toCommand(campaignId, request, userId, idempotencyKey,
                traceparent, tracestate);
        ReservationSubmissionResult result = submitReservation.submit(command);
        if (!result.isAccepted()) {
            throw new ReservationSubmissionException(result.outcome());
        }

        ReservationAcceptedResponse response = mapper.toAcceptedResponse(result.snapshot());
        URI location = URI.create("/api/v1/flash-sales/reservations/" + response.reservationId());
        String traceId = FlashSaleRequestContext.resolveTraceId(servletRequest);
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, location.toString())
                .header(FlashSaleRequestContext.TRACE_HEADER, traceId)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success("Reservation accepted", response));
    }
}
