package com.philia.flashsale.flashsale.reservation.adapter.in.web.mapper;

import com.philia.flashsale.flashsale.reservation.adapter.in.web.request.ReserveCampaignQuotaRequest;
import com.philia.flashsale.flashsale.reservation.adapter.in.web.response.ReservationAcceptedResponse;
import com.philia.flashsale.flashsale.reservation.application.command.SubmitReservationCommand;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps HTTP boundary records to application commands and durable snapshots to responses. */
@Mapper(componentModel = "spring")
public interface ReservationWebMapper {

    @Mapping(target = "status", constant = "RESERVED")
    ReservationAcceptedResponse toAcceptedResponse(AcceptedReservationSnapshot snapshot);

    default SubmitReservationCommand toCommand(UUID campaignId, ReserveCampaignQuotaRequest request,
            UUID userId, String idempotencyKey, String traceparent, String tracestate) {
        return new SubmitReservationCommand(campaignId, request.variantId(), userId, request.quantity(),
                idempotencyKey, traceparent, tracestate);
    }
}
